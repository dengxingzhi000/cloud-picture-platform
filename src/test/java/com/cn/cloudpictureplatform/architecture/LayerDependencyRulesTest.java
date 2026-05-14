package com.cn.cloudpictureplatform.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural layer dependency rules.
 * <p>
 * Expected dependency direction (strict):
 * <pre>
 * interfaces → application → domain ← infrastructure
 *                ↕                       ↕
 *         application → infrastructure → domain
 * </pre>
 * Known violations (documented in architecture report):
 * <ul>
 *   <li>interfaces → websocket (PictureController uses EditLockService, PresenceService)</li>
 *   <li>application → interfaces (application services import interfaces DTOs)</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.cn.cloudpictureplatform")
public class LayerDependencyRulesTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_application =
            noClasses()
                    .that().resideInAnyPackage("com.cn.cloudpictureplatform.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.cn.cloudpictureplatform.application..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_interfaces =
            noClasses()
                    .that().resideInAnyPackage("com.cn.cloudpictureplatform.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.cn.cloudpictureplatform.interfaces..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_websocket =
            noClasses()
                    .that().resideInAnyPackage("com.cn.cloudpictureplatform.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.cn.cloudpictureplatform.websocket..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_config =
            noClasses()
                    .that().resideInAnyPackage("com.cn.cloudpictureplatform.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.cn.cloudpictureplatform.config..");

}
