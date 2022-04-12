/*
 *  Copyright 2022 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.instancio.processor;

import org.instancio.InstancioMetaModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import java.io.BufferedWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("org.instancio.InstancioMetaModel")
public class InstancioAnnotationProcessor extends AbstractProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(InstancioAnnotationProcessor.class);

    private static final String MODEL_CLASSES_ATTRIBUTE = "classes";
    private static final MetaModelSourceGenerator sourceGenerator = new MetaModelSourceGenerator();

    private Messager messager;
    private Types types;
    private Elements elements;

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(InstancioMetaModel.class);
        LOG.debug("Preparing to process {} elements", elements.size());

        for (Element element : elements) {
            final TypeElement rootType = (TypeElement) element;

            final List<TypeMirror> modelClasses = getClassArrayValueFromAnnotation(
                    rootType, InstancioMetaModel.class, MODEL_CLASSES_ATTRIBUTE);

            for (TypeMirror typeMirror : modelClasses) {
                final Element modelClass = types.asElement(typeMirror);
                final String className = getClassName(modelClass);
                if (className == null) {
                    LOG.warn("Could not resolve class name for element: {}", element);
                } else {
                    writeSourceFile(new MetaModelClass(className, getFieldNames(modelClass)), element);
                }
            }
        }

        return true;
    }

    private void writeSourceFile(final MetaModelClass metaModelClass, final Element element) {
        final Filer filer = processingEnv.getFiler();
        final String filename = metaModelClass.getName() + "_";

        try (Writer writer = new BufferedWriter(filer.createSourceFile(filename, element).openWriter())) {
            LOG.debug("Generating metamodel class: {}", filename);
            writer.write(sourceGenerator.getSource(metaModelClass));
        } catch (Exception ex) {
            LOG.error("Error generating metamodel for class '{}'", metaModelClass, ex);
            messager.printMessage(Kind.WARNING,
                    "Instancio metamodel processor error: " + ex.getMessage());
        }
    }

    private static List<String> getFieldNames(@Nullable final Element element) {
        if (element == null) {
            return Collections.emptyList();
        }

        return element.getEnclosedElements()
                .stream()
                .filter(elem -> elem.getKind() == ElementKind.FIELD)
                .map(Object::toString)
                .collect(toList());
    }

    private static String getClassName(@Nullable final Element element) {
        if (element instanceof QualifiedNameable) {
            return ((QualifiedNameable) element).getQualifiedName().toString();
        }
        return element == null ? null : element.getSimpleName().toString();
    }

    private List<TypeMirror> getClassArrayValueFromAnnotation(final Element element,
                                                              final Class<? extends Annotation> annotation,
                                                              final String attributeName) {
        final List<TypeMirror> values = new ArrayList<>();

        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (types.isSameType(am.getAnnotationType(), elements.getTypeElement(annotation.getCanonicalName()).asType())) {

                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
                    if (attributeName.equals(entry.getKey().getSimpleName().toString())) {
                        final List<AnnotationValue> classesTypes = (List<AnnotationValue>) entry.getValue().getValue();
                        for (AnnotationValue annotationValue : classesTypes) {
                            values.add((TypeMirror) annotationValue.getValue());
                        }
                    }
                }
            }
        }
        return values;
    }
}
