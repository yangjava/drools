/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.compiler.integrationtests.phases;

import org.drools.compiler.builder.DroolsAssemblerContext;
import org.drools.compiler.builder.impl.BuildResultAccumulatorImpl;
import org.drools.compiler.builder.impl.DroolsAssemblerContextImpl;
import org.drools.compiler.builder.impl.GlobalVariableContext;
import org.drools.compiler.builder.impl.GlobalVariableContextImpl;
import org.drools.compiler.builder.impl.InternalKnowledgeBaseProvider;
import org.drools.compiler.builder.impl.KnowledgeBuilderConfigurationImpl;
import org.drools.compiler.builder.impl.PackageRegistryManagerImpl;
import org.drools.compiler.builder.impl.RootClassLoaderProvider;
import org.drools.compiler.builder.impl.TypeDeclarationBuilder;
import org.drools.compiler.builder.impl.TypeDeclarationContextImpl;
import org.drools.compiler.builder.impl.processors.AccumulateFunctionCompilationPhase;
import org.drools.compiler.builder.impl.processors.AnnotationNormalizer;
import org.drools.compiler.builder.impl.processors.CompilationPhase;
import org.drools.compiler.builder.impl.processors.ConsequenceCompilationPhase;
import org.drools.compiler.builder.impl.processors.EntryPointDeclarationCompilationPhase;
import org.drools.compiler.builder.impl.processors.FunctionCompilationPhase;
import org.drools.compiler.builder.impl.processors.FunctionCompiler;
import org.drools.compiler.builder.impl.processors.GlobalCompilationPhase;
import org.drools.compiler.builder.impl.processors.ImportCompilationPhase;
import org.drools.compiler.builder.impl.processors.ReteCompiler;
import org.drools.compiler.builder.impl.processors.RuleAnnotationNormalizer;
import org.drools.compiler.builder.impl.processors.RuleCompiler;
import org.drools.compiler.builder.impl.processors.RuleValidator;
import org.drools.compiler.builder.impl.processors.TypeDeclarationAnnotationNormalizer;
import org.drools.compiler.builder.impl.processors.TypeDeclarationCompilationPhase;
import org.drools.compiler.builder.impl.processors.WindowDeclarationCompilationPhase;
import org.drools.compiler.builder.impl.resources.DrlResourceHandler;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.impl.RuleBase;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.util.io.ClassPathResource;
import org.drools.drl.ast.descr.AttributeDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.parser.DroolsParserException;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.SessionsAwareKnowledgeBase;
import org.junit.Test;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.junit.Assert.fail;

public class ExplicitCompilerTest {

    @Test
    public void testCompile() throws DroolsParserException, IOException {
        Resource resource = new ClassPathResource("org/drools/compiler/integrationtests/phases/ExplicitCompilerTest.drl");

        int parallelRulesBuildThreshold = 0;
        InternalKnowledgeBase kBase = null;
        KnowledgeBuilderConfigurationImpl configuration = new KnowledgeBuilderConfigurationImpl();
        ClassLoader rootClassLoader = configuration.getClassLoader();

        BuildResultAccumulatorImpl results = new BuildResultAccumulatorImpl();

        RootClassLoaderProvider rootClassLoaderProvider = () -> rootClassLoader;
        InternalKnowledgeBaseProvider internalKnowledgeBaseProvider = () -> kBase;

        PackageRegistryManagerImpl packageRegistryManager =
                new PackageRegistryManagerImpl(
                        configuration, rootClassLoaderProvider, internalKnowledgeBaseProvider);

        TypeDeclarationContextImpl typeDeclarationContext =
                new TypeDeclarationContextImpl(configuration, packageRegistryManager);
        TypeDeclarationBuilder typeBuilder = new TypeDeclarationBuilder(typeDeclarationContext);
        typeDeclarationContext.setTypeDeclarationBuilder(typeBuilder);

        GlobalVariableContext globalVariableContext = new GlobalVariableContextImpl();

        DroolsAssemblerContext assemblerContext =
                new DroolsAssemblerContextImpl(
                        configuration,
                        rootClassLoader,
                        kBase,
                        globalVariableContext,
                        typeBuilder,
                        packageRegistryManager,
                        results);


        DrlResourceHandler handler = new DrlResourceHandler(configuration);
        final PackageDescr packageDescr = handler.process(resource);
        handler.getResults().forEach(results::addBuilderResult);


        PackageRegistry packageRegistry =
                packageRegistryManager.getOrCreatePackageRegistry(packageDescr);

        AnnotationNormalizer annotationNormalizer =
                AnnotationNormalizer.of(
                        packageRegistry.getTypeResolver(),
                        configuration.getLanguageLevel().useJavaAnnotations());



        Map<String, AttributeDescr> attributesForPackage =
                packageRegistryManager.getPackageAttributes().get(packageDescr.getNamespace());

        List<CompilationPhase> phases = asList(
                new ImportCompilationPhase(packageRegistry, packageDescr),
                new TypeDeclarationAnnotationNormalizer(annotationNormalizer, packageDescr),
                new EntryPointDeclarationCompilationPhase(packageRegistry, packageDescr),
                new AccumulateFunctionCompilationPhase(packageRegistry, packageDescr),
                new TypeDeclarationCompilationPhase(packageDescr, typeBuilder, packageRegistry),
                new WindowDeclarationCompilationPhase(packageRegistry, packageDescr, assemblerContext),
                new FunctionCompilationPhase(packageRegistry, packageDescr, configuration),
                new GlobalCompilationPhase(packageRegistry, packageDescr, kBase, globalVariableContext, null),
                new RuleAnnotationNormalizer(annotationNormalizer, packageDescr),
                /*         packageRegistry.setDialect(getPackageDialect(packageDescr)) */
                new RuleValidator(packageRegistry, packageDescr, configuration),
                new FunctionCompiler(packageDescr, packageRegistry, null, rootClassLoader),
                new RuleCompiler(packageRegistry, packageDescr, kBase, parallelRulesBuildThreshold,
                        null, attributesForPackage, resource, assemblerContext),
                new ReteCompiler(packageRegistry, packageDescr, kBase, null),
                new ConsequenceCompilationPhase(packageRegistryManager)
        );


        for (CompilationPhase phase : phases) {
            phase.process();
            phase.getResults().forEach(results::addBuilderResult);
            if (results.hasErrors()) {
                results.getErrors().forEach(System.out::println);
                fail("Found compilation errors at Phase " + phase.getClass().getSimpleName());
            }
        }


        List<InternalKnowledgePackage> packages =
                packageRegistryManager.getPackageRegistry().values()
                        .stream().map(PackageRegistry::getPackage).collect(Collectors.toList());
        RuleBase kbase = RuleBaseFactory.newRuleBase((KieBaseConfiguration)null);
        kbase.addPackages(packages);
        SessionsAwareKnowledgeBase sessionsAwareKnowledgeBase =
                new SessionsAwareKnowledgeBase(kbase);
        KieSession kieSession = sessionsAwareKnowledgeBase.newKieSession();


        kieSession.insert("HELLO");
        kieSession.fireAllRules();


    }

}
