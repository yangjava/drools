/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.modelcompiler.drlx;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.expr.Expression;
import org.drools.compiler.builder.impl.KnowledgeBuilderConfigurationImpl;
import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.compiler.compiler.DialectCompiletimeRegistry;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.util.io.InputStreamResource;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.modelcompiler.ExecutableModelProject;
import org.drools.modelcompiler.KJARUtils;
import org.drools.modelcompiler.builder.PackageModel;
import org.drools.modelcompiler.builder.generator.DRLIdGenerator;
import org.drools.modelcompiler.builder.generator.ModelGenerator;
import org.drools.mvel.DrlDumper;
import org.drools.mvel.parser.MvelParser;
import org.drools.mvel.parser.ParseStart;
import org.junit.Ignore;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.builder.KnowledgeBuilderResult;

import static org.drools.mvel.parser.Providers.provider;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DrlxCompilerTest {

    @Test
    public void testWhitespace() {
        ParseStart<Expression> context = ParseStart.EXPRESSION;
        MvelParser mvelParser = new MvelParser(new ParserConfiguration(), false);
        ParseResult<Expression> result = mvelParser.parse(context, provider("1\n+1"));
        assertEquals("1 + 1", result.getResult().get().toString());
    }

    @Test
    public void testSingleFileUnit() throws Exception {
        InputStream p = getClass().getClassLoader().getResourceAsStream("drlx1/Example.drlx");
        InputStreamResource r = new InputStreamResource(p);

        DrlxCompiler drlxCompiler = new DrlxCompiler();

        assertTrue("Should not have compiler errors\n" +
                           drlxCompiler.getResults().stream()
                                   .map(KnowledgeBuilderResult::toString)
                                   .collect(Collectors.joining("\n")),
                   drlxCompiler.getResults().isEmpty());

        PackageDescr packageDescr = drlxCompiler.toPackageDescr(r);
        System.out.println(new DrlDumper().dump(packageDescr));

    }

    @Test
    @Ignore("Rule Unit compiler is not available in Drools 8 yet")
    public void testCompileUnit() throws IOException {
        InputStream p = getClass().getClassLoader().getResourceAsStream("drlx1/Example.drlx");
        InputStreamResource r = new InputStreamResource(p);

        DrlxCompiler drlxCompiler = new DrlxCompiler();
        PackageDescr packageDescr = drlxCompiler.toPackageDescr(r);

        assertTrue("Should not have compiler errors", drlxCompiler.getResults().isEmpty());

        KnowledgeBuilderImpl kbuilder = new KnowledgeBuilderImpl();
        PackageRegistry registry = kbuilder.getOrCreatePackageRegistry(packageDescr);
        kbuilder.getAndRegisterTypeDeclaration(
                org.drools.modelcompiler.drlx.Example.class,
                "org.drools.modelcompiler.drlx");
        InternalKnowledgePackage knowledgePackage = registry.getPackage();
        PackageModel packageModel =
                new PackageModel(
                        "com.example:dummy:1.0.0",
                        packageDescr.getName(),
                        new KnowledgeBuilderConfigurationImpl(),
                        new DialectCompiletimeRegistry(),
                        new DRLIdGenerator());
        ModelGenerator.generateModel(
                kbuilder,
                knowledgePackage,
                packageDescr,
                packageModel);

        assertEquals(1, packageModel.getRuleUnits().size());
    }

    @Test
    @Ignore("Rule Unit Executor is not available in Drools 8 yet")
    public void testCompileUnitFull() throws IOException {
        String path = "drlx1/Example.drlx";
        InputStream p = getClass().getClassLoader().getResourceAsStream(path);
        InputStreamResource r = new InputStreamResource(p);
        r.setSourcePath("src/main/resources/" + path);

        KieServices ks = KieServices.get();
        ReleaseId releaseId = ks.newReleaseId("org.kie", "kjar-test-1.0", "1.0");
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write(r);
        kfs.writePomXML(KJARUtils.getPom(releaseId));
        KieBuilder kieBuilder = ks.newKieBuilder(kfs).buildAll(ExecutableModelProject.class);
        KieContainer kieContainer = ks.newKieContainer(releaseId);
//        RuleUnitExecutorImpl executor = new RuleUnitExecutorImpl((RuleBase) kieContainer.getKieBase(),
//                (SessionConfiguration) kieContainer.getKieSessionConfiguration());
//        executor.newDataSource("dates",
//                               LocalDate.of(2021, 1, 1));
//        assertEquals(3, executor.run(Example.class));
    }

}
