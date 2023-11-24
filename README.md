# Hyperledger Fabric REST Integration

## Description:-
<p>This artifact provides a mechanism to invoke and query fabric chaincode using a REST-based API interface.<br>
Additionally, it can also invoke chaincode using an asynchronous method and can publish chaincode events to Kafka/Event-Hub topics.</p>

## Key Feature:-</br>
1. Invoke Chaincode with REST.</br>
2. Query Chaincode with REST.</br>
3. Invoke Chaincode with Kafka/Event-Hub.</br>
4. Publish chaincode events from multiple channels to Kafka/Event-Hub.</br>

## Prerequisites:-</br>
1. Fabric 2.x network.</br>
2. Connection Profile YAML file.</br>
3. Wallet (.id) file.</br>
4. Java and Maven is installed.
5. (Optional) Kafka/Event-Hub configuration for invoking chaincode asynchronously.</br>
6. (Optional) Kafka/Event-Hub configuration for publishing chaincode events.</br>

## Running Locally:-</br>
1. Download/Clone the repository and build the project using ``mvn clean install``
2. Create a folder named ***wallet*** in the root directory of the project.</br>
3. Add the ``connection-org1.yaml`` file, Usually located at ``test-network/organizations/peerOrganizations/org1.example.com`` if using the test network from [fabric-samples](https://github.com/hyperledger/fabric-samples) to the wallet folder.</br>
4. Create the ``admin.id`` and ``clientUser.id`` files in the ***wallet*** folder. Paste the code below in the file and update the ***certificates*** and the ***private key*** from the test network.</br> Usually located in ``organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp`` </br> You can use this command to extract the certificate and key from the file: ``awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}' filename``
   ```
   {
       "credentials":
       {
          "certificate":"",
          "privateKey":""
       },
       "type":"X.509",
       "version":1,
       "mspId":"Org1MSP"
   }
   ```
5. Update the ***wallet***, ***orgConnectionConfig*** and ***server config*** path to your ***absolute path*** in the ``application.yml`` file present in ``src/main/resources``.
6. Make sure the Peer URL and CA URL in ``connection-org1.yaml`` are reachable.</br>
7. Run, <i>hlf.java.rest.client.FabricClientBootstrap</i> java file or jar file.
8. You can also run as container using ``docker-compose up``.</br> If the fabric network is running local, make sure the docker-compose.yml file is configured to use the correct network.</br>
    ```
    networks:
    default:
    external:
      name: <fabric's network>
    ```
## Event-driven Design

### Asynchronous Integration to invoke chaincode
This component supports event-based architecture by consuming transactions through Kafka & Azure EventHub. 
To configure it, use the below configuration in the application.yml file.
```
kafka:
  integration-points:
  -
    brokerHost: <Hostname1 with Port>
    groupId: <Group ID>
    topic: <Topic Name>
  -
    brokerHost: <Hostname2 with Port>
    groupId: <Group ID>
    topic: <Topic Name>
    # For Azure EventHub
    jaasConfig: org.apache.kafka.common.security.plain.PlainLoginModule required username="$ConnectionString" password="Endpoint=sb://<Hostname>/;SharedAccessKeyName=<key-name>;SharedAccessKey=<key-value>";
    # For SOX compliant Kafka Clusters
    ssl-enabled: true
    security-protocol: SSL
    ssl-keystore-location: <YOUR_SSL_KEYSTORE_PATH>
    ssl-keystore-password: <YOUR_SSL_PASSWORDH>
    ssl-truststore-location: <YOUR_SSL_TRUSTSTORE_PATH>
    ssl-truststore-password: <YOUR_SSL_TRUSTSTORE_PASSWORD>
    ssl-key-password: <YOUR_SSL_KEY_PASSWORD>
```
The component accepts JSON payload and 3 headers to invoke the chaincode.
Please find below the keys for the headers:-
```
1. channel_name
2. function_name
3. chaincode_name
```

### Capture Chaincode events:-
This component supports capturing chaincode events and publish it to Kafka or Azure EventHub. This can be useful for integrating with off-chain DB.
To configure it, use the below configuration in the application.yml file.
```
fabric:
  events:
    enabled: true
    chaincode: mychannel1,mychannel2  #Comma-separated list for listening to events from multiple channels 
kafka:
  event-listener:
    brokerHost: <Hostname with Port>
    topic: <Topic Name>
    # For Azure EventHub
    jaasConfig: org.apache.kafka.common.security.plain.PlainLoginModule required username="$ConnectionString" password="Endpoint=sb://<Hostname>/;SharedAccessKeyName=<key-name>;SharedAccessKey=<key-value>";
    # For SOX compliant Kafka Clusters
    ssl-enabled: true
    security-protocol: SSL
    ssl-keystore-location: <YOUR_SSL_KEYSTORE_PATH>
    ssl-keystore-password: <YOUR_SSL_PASSWORDH>
    ssl-truststore-location: <YOUR_SSL_TRUSTSTORE_PATH>
    ssl-truststore-password: <YOUR_SSL_TRUSTSTORE_PASSWORD>
    ssl-key-password: <YOUR_SSL_KEY_PASSWORD>
```
The component will send the same JSON payload sent by the chaincode and add the following headers.

```
1. fabric_tx_id
2. event_name
3. channel_name
4. event_type (value: chaincode_event)
```

### Capture Block events:-
This component supports capturing block events and publish it to Kafka or Azure EventHub. This can be useful for integrating with off-chain DB where adding events to chaincode is not possible (for ex - Food-Trust anchor channel).
To configure it, use the below configuration in the application.yml file.
```
fabric:
  events:
    enabled: true
    block: mychannel1,mychannel2  #Comma-separated list for listening to events from multiple channels 
kafka:
  event-listener:
    brokerHost: <Hostname with Port>
    topic: <Topic Name>
    # For Azure EventHub
    jaasConfig: org.apache.kafka.common.security.plain.PlainLoginModule required username="$ConnectionString" password="Endpoint=sb://<Hostname>/;SharedAccessKeyName=<key-name>;SharedAccessKey=<key-value>";
    # For SOX compliant Kafka Clusters
    ssl-enabled: true
    security-protocol: SSL
    ssl-keystore-location: <YOUR_SSL_KEYSTORE_PATH>
    ssl-keystore-password: <YOUR_SSL_PASSWORDH>
    ssl-truststore-location: <YOUR_SSL_TRUSTSTORE_PATH>
    ssl-truststore-password: <YOUR_SSL_TRUSTSTORE_PASSWORD>
    ssl-key-password: <YOUR_SSL_KEY_PASSWORD>
```
The component will send the same JSON payload sent by the chaincode and add the following headers.

```
1. fabric_tx_id
2. channel_name
3. chaincode name
4. function_name
5. event_type (value: block_event)
```
