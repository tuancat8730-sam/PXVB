package vn.pxvb.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Kiểm thử kiến trúc bắt buộc (SDD §2.4/§16.5): giữ ranh giới module và hướng phụ thuộc. */
@AnalyzeClasses(packages = "vn.pxvb", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule buildingBlocksDoNotDependOnModules = noClasses()
            .that().resideInAPackage("vn.pxvb.common..")
            .should().dependOnClassesThat().resideInAnyPackage("vn.pxvb.identity..", "vn.pxvb.workflow..",
                    "vn.pxvb.alerting..");

    @ArchTest
    static final ArchRule modulesUseIdentityOnlyThroughContract = noClasses()
            .that().resideInAnyPackage("vn.pxvb.workflow..", "vn.pxvb.alerting..")
            .should().dependOnClassesThat().resideInAnyPackage("vn.pxvb.identity.domain..",
                    "vn.pxvb.identity.application..", "vn.pxvb.identity.infrastructure..", "vn.pxvb.identity.api..");

    @ArchTest
    static final ArchRule masterdataAndDataioAreIndependentOfOtherBusinessModules = noClasses()
            .that().resideInAnyPackage("vn.pxvb.masterdata..", "vn.pxvb.dataio..")
            .should().dependOnClassesThat().resideInAnyPackage("vn.pxvb.identity..", "vn.pxvb.workflow..",
                    "vn.pxvb.alerting..");

    @ArchTest
    static final ArchRule dataioDoesNotDependOnMasterdata = noClasses()
            .that().resideInAPackage("vn.pxvb.dataio..")
            .should().dependOnClassesThat().resideInAPackage("vn.pxvb.masterdata..");

    @ArchTest
    static final ArchRule alertingDoesNotDependOnWorkflow = noClasses()
            .that().resideInAPackage("vn.pxvb.alerting..")
            .should().dependOnClassesThat().resideInAPackage("vn.pxvb.workflow..");

    @ArchTest
    static final ArchRule workflowDoesNotDependOnAlerting = noClasses()
            .that().resideInAPackage("vn.pxvb.workflow..")
            .should().dependOnClassesThat().resideInAPackage("vn.pxvb.alerting..");

    @ArchTest
    static final ArchRule newModulesKeepDomainIndependent = noClasses()
            .that().resideInAnyPackage("vn.pxvb.workflow.domain..", "vn.pxvb.alerting.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("vn.pxvb.workflow.api..", "vn.pxvb.workflow.application..",
                    "vn.pxvb.alerting.api..", "vn.pxvb.alerting.application..", "vn.pxvb.alerting.infrastructure..");

    @ArchTest
    static final ArchRule newModulesApiLayerNotUsedByOthers = noClasses()
            .that().resideOutsideOfPackages("vn.pxvb.workflow.api..", "vn.pxvb.alerting.api..")
            .should().dependOnClassesThat().resideInAnyPackage("vn.pxvb.workflow.api..", "vn.pxvb.alerting.api..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("vn.pxvb.identity.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("vn.pxvb.identity.api..", "vn.pxvb.identity.application..",
                    "vn.pxvb.identity.infrastructure..");

    @ArchTest
    static final ArchRule apiLayerIsNotUsedByOtherLayers = noClasses()
            .that().resideOutsideOfPackage("vn.pxvb.identity.api..")
            .should().dependOnClassesThat().resideInAPackage("vn.pxvb.identity.api..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnApi = noClasses()
            .that().resideInAPackage("vn.pxvb.identity.application..")
            .should().dependOnClassesThat().resideInAPackage("vn.pxvb.identity.api..");
}
