package com.cn.cloudpictureplatform.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural layer dependency rules.
 * <p>
 * Expected dependency direction:
 * <pre>
 * interfaces → application → domain ← infrastructure
 * </pre>
 * Known violations (documented for future cleanup):
 * <ul>
 *   <li>application → interfaces (152 times): application services import DTOs from interfaces package</li>
 *   <li>application → websocket (102 times): application services import NotificationPublisher from websocket</li>
 *   <li>interfaces → infrastructure (116 times): controllers import AppUserPrincipal from infrastructure.security</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.cn.cloudpictureplatform")
public class LayerDependencyRulesTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_application =
            noClasses().that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("..application..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_interfaces =
            noClasses().that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("..interfaces..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_websocket =
            noClasses().that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("..websocket..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_config =
            noClasses().that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("..config..");
}
