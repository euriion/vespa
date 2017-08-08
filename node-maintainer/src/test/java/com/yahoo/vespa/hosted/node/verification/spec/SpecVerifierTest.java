package com.yahoo.vespa.hosted.node.verification.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.mock.MockCommandExecutor;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoInfoRetriever;
import com.yahoo.vespa.hosted.node.verification.spec.noderepo.NodeRepoJsonModel;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.yamasreport.YamasSpecReport;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpecVerifierTest {

    private MockCommandExecutor mockCommandExecutor;
    private static final String ABSOLUTE_PATH = Paths.get(".").toAbsolutePath().normalize().toString();
    private static final String RESOURCE_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources";
    private static final String URL_RESOURCE_PATH = "file://" + ABSOLUTE_PATH + "/" + RESOURCE_PATH;
    private static final String NODE_REPO_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/nodeInfoTest.json";
    private static final String YAMAS_REPORT_PATH = "src/test/java/com/yahoo/vespa/hosted/node/verification/spec/resources/SpecVerifierReport";
    private static final String CPU_INFO_PATH = RESOURCE_PATH + "/cpuinfoTest";
    private static final String MEMORY_INFO_PATH = RESOURCE_PATH + "/meminfoTest";
    private static final String DISK_TYPE_INFO_PATH = RESOURCE_PATH + "/DiskTypeFastDisk";
    private static final String DISK_SIZE_INFO_PATH = RESOURCE_PATH + "/filesize";
    private static final String NET_INTERFACE_INFO_PATH = RESOURCE_PATH + "/ifconfig";
    private static final String NET_INTERFACE_SPEED_INFO_PATH = RESOURCE_PATH + "/eth0";
    private static final String PING_RESPONSE = RESOURCE_PATH + "/validpingresponse";
    private static final String INVALID_PING_RESPONSE = RESOURCE_PATH + "/pingresponse-all-packets-lost";
    private static final double DELTA = 0.1;

    @Before
    public void setup() {
        mockCommandExecutor = new MockCommandExecutor();
    }


    @Test
    @Ignore
    public void verifySpec_equal_nodeRepoInfo_and_hardware_should_return_true() throws Exception {
        mockCommandExecutor.addCommand("echo notUsed " + URL_RESOURCE_PATH);
        mockCommandExecutor.addCommand("echo nodeRepo.json");
        mockCommandExecutor.addCommand("cat " + CPU_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + MEMORY_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + DISK_TYPE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + DISK_SIZE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_SPEED_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + PING_RESPONSE);
        assertTrue(SpecVerifier.verifySpec(mockCommandExecutor));
    }

    @Test
    public void verifySpec_environment_is_virtual_machine_should_return_true() throws Exception {
        mockCommandExecutor.addCommand("echo notUsed " + URL_RESOURCE_PATH);
        mockCommandExecutor.addCommand("echo nodeRepoVirtualMachine.json");
        assertTrue(SpecVerifier.verifySpec(mockCommandExecutor));
    }

    @Test
    public void verifySpec_unequal_nodeRepoInfo_and_hardware_should_return_false() throws Exception {
        mockCommandExecutor.addCommand("echo notUsed " + URL_RESOURCE_PATH);
        mockCommandExecutor.addCommand("echo nodeRepo.json");
        mockCommandExecutor.addCommand("cat " + CPU_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + MEMORY_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + DISK_TYPE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + DISK_SIZE_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_INFO_PATH + "NoIpv6");
        mockCommandExecutor.addCommand("cat " + NET_INTERFACE_SPEED_INFO_PATH);
        mockCommandExecutor.addCommand("cat " + INVALID_PING_RESPONSE);
        assertFalse(SpecVerifier.verifySpec(mockCommandExecutor));
    }

    @Test
    public void makeYamasSpecReport_should_return_false_interface_speed_and_ipv6_connection() throws Exception {
        HardwareInfo actualHardware = new HardwareInfo();
        actualHardware.setMinCpuCores(24);
        actualHardware.setMinMainMemoryAvailableGb(24);
        actualHardware.setInterfaceSpeedMbs(10009); //this is wrong
        actualHardware.setMinDiskAvailableGb(500);
        actualHardware.setIpv4Interface(true);
        actualHardware.setIpv6Interface(false);
        actualHardware.setIpv6Connection(true);
        actualHardware.setDiskType(HardwareInfo.DiskType.SLOW);
        ArrayList<URL> url = new ArrayList<>(Arrays.asList(new File(NODE_REPO_PATH).toURI().toURL()));
        NodeRepoJsonModel nodeRepoJsonModel = NodeRepoInfoRetriever.retrieve(url);
        YamasSpecReport yamasSpecReport = SpecVerifier.makeYamasSpecReport(actualHardware, nodeRepoJsonModel);
        long timeStamp = 1501504035;
        yamasSpecReport.setTimeStamp(timeStamp);
        String expectedJson = MockCommandExecutor.readFromFile(YAMAS_REPORT_PATH).get(0);
        ObjectMapper om = new ObjectMapper();
        String actualJson = om.writeValueAsString(yamasSpecReport);
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void getNodeRepositoryJSON_should_return_valid_nodeRepoJSONModel() throws Exception {
        mockCommandExecutor.addCommand("echo notUsed " + URL_RESOURCE_PATH);
        mockCommandExecutor.addCommand("echo nodeRepo.json");
        NodeRepoJsonModel actualNodeRepoJsonModel = SpecVerifier.getNodeRepositoryJSON(mockCommandExecutor);
        double expectedMinCpuCores = 4D;
        double expectedMinMainMemoryAvailableGb = 4.04D;
        double expectedMinDiskAvailableGb = 1759.84;
        boolean expectedFastDisk = true;
        String expectedIpv6Address = "2001:4998:c:2940:0:0:0:111c";
        assertEquals(expectedIpv6Address, actualNodeRepoJsonModel.getIpv6Address());
        assertEquals(expectedMinCpuCores, actualNodeRepoJsonModel.getMinCpuCores(), DELTA);
        assertEquals(expectedMinMainMemoryAvailableGb, actualNodeRepoJsonModel.getMinMainMemoryAvailableGb(), DELTA);
        assertEquals(expectedMinDiskAvailableGb, actualNodeRepoJsonModel.getMinDiskAvailableGb(), DELTA);
        assertEquals(expectedFastDisk, actualNodeRepoJsonModel.isFastDisk());
    }

}