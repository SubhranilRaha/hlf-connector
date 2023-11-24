package hlf.java.rest.client.IT;

import hlf.java.rest.client.model.*;
import hlf.java.rest.client.model.Orderer;
import hlf.java.rest.client.service.ChaincodeOperationsService;
import hlf.java.rest.client.service.ChannelService;
import hlf.java.rest.client.service.HFClientWrapper;
import hlf.java.rest.client.service.TransactionFulfillment;
import org.apache.commons.io.FileUtils;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TransactionIT {
    @Autowired ChaincodeOperationsService chaincodeOperationsService;
    @Autowired ChannelService channelService;
    @Autowired TransactionFulfillment transactionFulfillment;
    @Autowired private HFClientWrapper hfClientWrapper;
    private static final String NETWORK_NAME = "fabricsetup_default";
    private static final String CHANNEL_NAME = "trx-test";
    private static final String CHAINCODE_NAME = "example-cc_1";
    private static final String CHAINCODE_VERSION = "1";

    private void setupChannelAndChaincode() throws InvalidArgumentException, ProposalException, IOException {
        ChannelOperationRequest channelOperationRequest = new ChannelOperationRequest();
        channelOperationRequest.setChannelName(CHANNEL_NAME);
        channelOperationRequest.setConsortiumName("SampleConsortium");
        try {
            Orderer orderer = new Orderer();
            orderer.setName("orderer1");
            orderer.setGrpcUrl("grpc://localhost:7050");
            orderer.setCertificate(
                    FileUtils.readFileToString(
                            Paths.get(
                                            "src/test/java/fabricSetup/e2e-2Orgs/v2.1/crypto-config/ordererOrganizations/example.com/msp/admincerts/Admin@example.com-cert.pem")
                                    .toFile()));
            List<Orderer> ordererList = new ArrayList<>();
            ordererList.add(orderer);
            channelOperationRequest.setOrderers(ordererList);

            hlf.java.rest.client.model.Peer peer = new hlf.java.rest.client.model.Peer();
            peer.setName("peer1");
            peer.setMspid("Org1MSP");
            peer.setGrpcUrl("grpc://localhost:7051");
            String admincert =
                    FileUtils.readFileToString(
                            Paths.get(
                                            "src/test/java/fabricSetup/e2e-2Orgs/v2.1/crypto-config/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/msp/admincerts/Admin@org1.example.com-cert.pem")
                                    .toFile());
            String cacert =
                    FileUtils.readFileToString(
                            Paths.get(
                                            "src/test/java/fabricSetup/e2e-2Orgs/v2.1/crypto-config/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/msp/cacerts/ca.org1.example.com-cert.pem")
                                    .toFile());
            peer.setCertificate(cacert);

            MSPDTO mspdto = new MSPDTO();
            List<String> rootCerts = new ArrayList<>();
            rootCerts.add(cacert);
            mspdto.setRootCerts(rootCerts);
            List<String> tlsRootCerts = new ArrayList<>();
            tlsRootCerts.add(cacert);
            mspdto.setTlsRootCerts(tlsRootCerts);
            mspdto.setAdminOUCert(admincert);
            mspdto.setClientOUCert(admincert);
            mspdto.setPeerOUCert(cacert);
            peer.setMspDTO(mspdto);

            List<hlf.java.rest.client.model.Peer> peerList = new ArrayList<>();
            peerList.add(peer);
            channelOperationRequest.setPeers(peerList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        channelService.createChannel(channelOperationRequest);
        channelService.joinChannel(channelOperationRequest);
        Channel channel = hfClientWrapper.getHfClient().getChannel(CHANNEL_NAME);
        //Package the chaincode
        LifecycleChaincodePackage lifecycleChaincodePackage =
                LifecycleChaincodePackage.fromSource(
                        CHAINCODE_NAME,
                        Paths.get("src/test/java/hlf/java/rest/client/chaincode/"),
                        TransactionRequest.Type.JAVA,
                        "",
                        null);
        //Install the chaincode
        String packageId =
                    lifecycleInstallChaincode(
                            hfClientWrapper.getHfClient(), channel.getPeers(), lifecycleChaincodePackage);

        //Get the sequence and build the chaincode operation
        String sequence =
                chaincodeOperationsService.getCurrentSequence(
                        CHANNEL_NAME, CHAINCODE_NAME, CHAINCODE_VERSION);
        ChaincodeOperations chaincodeOperations =
                ChaincodeOperations.builder()
                        .chaincodeName(CHAINCODE_NAME)
                        .chaincodePackageID(packageId)
                        .chaincodeVersion(CHAINCODE_VERSION)
                        .initRequired(false)
                        .sequence(Long.parseLong(sequence))
                        .build();
        //Approve the chaincode
                chaincodeOperationsService.performChaincodeOperation(
                        CHANNEL_NAME, chaincodeOperations, ChaincodeOperationsType.APPROVE, Optional.empty());
        //Commit the chaincode
                chaincodeOperationsService.performChaincodeOperation(
                        CHANNEL_NAME, chaincodeOperations, ChaincodeOperationsType.COMMIT, Optional.empty());
    }

    private String lifecycleInstallChaincode(
            HFClient client, Collection<Peer> peers, LifecycleChaincodePackage lifecycleChaincodePackage)
            throws InvalidArgumentException, ProposalException {

        LifecycleInstallChaincodeRequest installProposalRequest =
                client.newLifecycleInstallChaincodeRequest();
        installProposalRequest.setLifecycleChaincodePackage(lifecycleChaincodePackage);
        installProposalRequest.setProposalWaitTime(1000000);

        Collection<LifecycleInstallChaincodeProposalResponse> responses =
                client.sendLifecycleInstallChaincodeRequest(installProposalRequest, peers);

        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        String packageID = null;
        for (LifecycleInstallChaincodeProposalResponse response : responses) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                successful.add(response);
                if (packageID == null) {
                    packageID = response.getPackageId();
                    assertNotNull(
                            format("Hashcode came back as null from peer: %s ", response.getPeer()), packageID);
                } else {
                    assertEquals(
                            "Miss match on what the peers returned back as the packageID",
                            packageID,
                            response.getPackageId());
                }
            } else {
                failed.add(response);
            }
        }

        if (!failed.isEmpty()) {
            ProposalResponse first = failed.iterator().next();
            fail("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
        }

        assertNotNull(packageID);
        assertFalse(packageID.isEmpty());

        return packageID;
    }

    @Test
    @Order(1)
    public void initializeSmartContract() throws InvalidArgumentException, ProposalException, IOException {
        setupChannelAndChaincode();
        ResponseEntity<ClientResponseModel> response = transactionFulfillment.initSmartContract(
                NETWORK_NAME, CHAINCODE_NAME, "createMyAsset", Optional.empty(),"1","Asset1");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    @Test
    @Order(2)
    public void postTransactionToChaincode() {
        ResponseEntity<ClientResponseModel> response = transactionFulfillment.writeTransactionToLedger(
                NETWORK_NAME, CHAINCODE_NAME, "createMyAsset", Optional.empty(),"1","Asset1");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(3)
    public void getTransactionFromChaincode() {
        ResponseEntity<ClientResponseModel> response = transactionFulfillment.readTransactionFromLedger(
                NETWORK_NAME, CHAINCODE_NAME, "createMyAsset", "transactionId");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
