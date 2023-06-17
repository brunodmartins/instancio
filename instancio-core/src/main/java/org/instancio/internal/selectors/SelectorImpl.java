/*
 * Copyright 2022-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.instancio.internal.selectors;

import org.instancio.GroupableSelector;
import org.instancio.Scope;
import org.instancio.Selector;
import org.instancio.TargetSelector;
import org.instancio.internal.ApiValidator;
import org.instancio.internal.Flattener;
import org.instancio.internal.util.Format;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SelectorImpl
        implements Selector, GroupableSelector, Flattener<TargetSelector>, UnusedSelectorDescription {

    private final Class<?> targetClass;
    private final String fieldName;
    private final List<Scope> scopes;
    private final Selector parent;
    private final Throwable stackTraceHolder;
    private final boolean isRoot;
    private final Integer depth;

    /**
     * Constructor.
     *
     * @param targetClass      target class
     * @param fieldName        field name, applicable to field selectors only
     * @param scopes           scopes specified top-down, from outermost to innermost
     * @param parent           required only for {@link PrimitiveAndWrapperSelectorImpl} for checking unused selectors
     * @param stackTraceHolder stacktrace for reporting locations of unused selectors
     */
    private SelectorImpl(@Nullable final Class<?> targetClass,
                         @Nullable final String fieldName,
                         @Nullable final List<Scope> scopes,
                         @Nullable final Selector parent,
                         @Nullable final Throwable stackTraceHolder,
                         final boolean isRoot) {

        this.targetClass = targetClass;
        this.fieldName = fieldName;
        this.scopes = scopes == null ? Collections.emptyList() : Collections.unmodifiableList(scopes);
        this.parent = parent;
        this.stackTraceHolder = stackTraceHolder;
        this.isRoot = isRoot;
        this.depth = null; // NOPMD
    }

    SelectorImpl(@Nullable final Class<?> targetClass,
                 @Nullable final String fieldName,
                 final boolean isRoot) {

        this(targetClass, fieldName, Collections.emptyList(), null, new Throwable(), isRoot);
    }

    private SelectorImpl(final Builder builder) {
        targetClass = builder.targetClass;
        fieldName = builder.fieldName;
        scopes = builder.scopes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(builder.scopes);
        parent = builder.parent;
        stackTraceHolder = builder.stackTraceHolder == null ? new Throwable() : builder.stackTraceHolder;
        isRoot = builder.isRoot;
        depth = builder.depth;
    }

    public static Builder builder(final SelectorImpl copy) {
        Builder builder = new Builder();
        builder.targetClass = copy.getTargetClass();
        builder.fieldName = copy.getFieldName();
        builder.scopes = copy.getScopes();
        builder.parent = copy.getParent();
        builder.stackTraceHolder = copy.getStackTraceHolder();
        builder.depth = copy.depth;
        return builder;
    }

    // avoid naming the method 'root()' so it doesn't appear in IDE completion suggestions
    // which can be confused with the public API method 'Selector.root()'
    public static SelectorImpl getRootSelector() {
        return new SelectorImpl(null, null, true);
    }

    public boolean isRoot() {
        return isRoot;
    }

    public Throwable getStackTraceHolder() {
        return stackTraceHolder;
    }

    @Override
    public TargetSelector atDepth(final int depth) {
        return builder(this)
                .depth(ApiValidator.validateDepth(depth))
                .build();
    }

    @Override
    public String getDescription() {
        return String.format("%s%n    at %s", this, Format.firstNonInstancioStackTraceLine(stackTraceHolder));
    }

    @Override
    public Selector within(@NotNull final Scope... scopes) {
        return builder(this).scopes(Arrays.asList(scopes)).build();
    }

    @Override
    public Scope toScope() {
        return new ScopeImpl(targetClass, fieldName);
    }

    @Override
    public List<TargetSelector> flatten() {
        return Collections.singletonList(this);
    }

    public Selector getParent() {
        return parent;
    }

    public List<Scope> getScopes() {
        return scopes;
    }

    public boolean isFieldSelector() {
        return fieldName != null;
    }

    public Class<?> getTargetClass() {
        return targetClass;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Integer getDepth() {
        return depth;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs equality check with all fields except {@code parent} and {@code stackTraceHolder}.
     */
    @Override
    public final boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof SelectorImpl)) return false;
        final SelectorImpl that = (SelectorImpl) o;
        return Objects.equals(targetClass, that.targetClass)
                && Objects.equals(fieldName, that.fieldName)
                && Objects.equals(scopes, that.scopes)
                && Objects.equals(depth, that.depth)
                && isRoot == that.isRoot;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Calculates hashcode using all fields except {@code parent} and {@code stackTraceHolder}.
     */
    @Override
    public final int hashCode() {
        return Objects.hash(targetClass, fieldName, scopes, depth, isRoot);
    }

    @Override
    public String toString() {
        if (isRoot) return "root()";

        final StringBuilder sb = new StringBuilder();

        if (parent instanceof PrimitiveAndWrapperSelectorImpl) {
            sb.append(parent);
        } else {
            sb.append(isFieldSelector() ? "field(" : "all(");

            if (targetClass != null) {
                sb.append(targetClass.getSimpleName());
            }
            if (fieldName != null) {
                if (targetClass != null) {
                    sb.append(", ");
                }
                sb.append('"').append(fieldName).append('"');
            }
            sb.append(')');

            // can only have depth or scopes, but not both
            if (depth != null) {
                sb.append(".atDepth(").append(depth).append(')');
            } else if (!scopes.isEmpty()) {
                sb.append(", ").append(Format.formatScopes(scopes));
            }
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Class<?> targetClass;
        private String fieldName;
        private List<Scope> scopes;
        private Selector parent;
        private Throwable stackTraceHolder;
        private boolean isRoot;
        private Integer depth;

        private Builder() {
        }

        public Builder targetClass(final Class<?> targetClass) {
            this.targetClass = targetClass;
            return this;
        }

        public Builder fieldName(final String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder scopes(final List<Scope> scopes) {
            this.scopes = scopes;
            return this;
        }

        public Builder parent(final Selector parent) {
            this.parent = parent;
            return this;
        }

        public Builder stackTraceHolder(final Throwable stackTraceHolder) {
            this.stackTraceHolder = stackTraceHolder;
            return this;
        }

        public Builder root(final boolean isRoot) {
            this.isRoot = isRoot;
            return this;
        }

        public Builder depth(final Integer depth) {
            this.depth = depth;
            return this;
        }

        public SelectorImpl build() {
            return new SelectorImpl(this);
        }
    }
}
