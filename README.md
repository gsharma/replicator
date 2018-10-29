# Replicator

## Background
The Replicator is an automaton to provide asynchronous streaming replication between potentially geographically distributed datastore instances. It is a lower-than-application-level data sync-service that can run in one of transmit, receive or transceive modes as a side-car standalone process on either side of a LAN or WAN-based transport. Both the transmitter/streamer and receiver are implemented to use non-blocking I/O over HTTP. The replication protocol as designed, is independent of the choice of the underlying data-store it is implemented on. For the initial phase, the replicator spans [Corfu](https://github.com/CorfuDB/CorfuDB) db clusters spread across multiple remote sites.

## Key Design Tenets

### Do No Evil Modes
The Replicator provides no support for Byzantine fault tolerance. At the moment, the Replicator assumes Fail-Stop mode of operation. Besides practical concerns with trust management and extension through the layers, this keeps with the design tenet of simplicity and debug-ability.

### Fire-and-Forget Transmitters
Replicator running in Transmitter mode does not wait for any synchronous or asynchronous acknowledgement in response to events sent over the wire. It delegates concerns such as flow and congestion control to TCP and works via a NACK protocol (described later). 

### Transports
Transports are easily pluggable for the most part but practical concerns dictate a deliberate preference towards using HTTP over WAN.

### Automaton
The Replicator itself is trivially an Automaton and wiring for [FSM](https://github.com/gsharma/state-machine) is sprinkled throughout the core service pieces. My FSM implementation is Java9 based and hence temporarily fenced off until such time that the Replicator can be upgraded, as well. The FSM will help with the replication stages - eg. bootstrapping, remote channel establishment, handshaking/negotiation of streams to sync, lifecycle management, etc. Note that the FSM is typically best configured to reset itself to init state on failures - it can do that as a hard-reset or a step-wise state unrolling, the former being the preferred mode.

### Concurrency & Throughput
Streamers can be trivially made concurrent with a 1:1 cardinality of stream-worker:stream thereby greatly improving throughput and concurrency. Some simple constraints provide good performance and correctness - always have no more than 1 stream-worker per stream. On the receiver side, a channel pipeline works single threaded thus obviating the need for any reentrant write locks while replaying log events against the remote/receiver's corfu server.

### Transparent Streaming
The Replicator makes no attempt to derive structure or semantic from log events read from the sender's datastore log. This has important implications on concerns like event conflation, deduping, etc with the additional overhead associated with constant up-keep with changing business requirements and domain understanding shoved down Replicator's throat. That said, it does need to understand and process replication events in line with the database's (not application's) log format.

### Minimum Dependencies
Since the Replicator has a very specialized job in this universe, there exist no reasons to bloat its dependency hell via 3rd party lib nonsense. Dependency hell is evil and unacceptable.

## Protocol
For the sake of describing the protocol, let's assume a simple system with 2 Replicators across a WAN - 1 each in intended to be running in TRANSMITTER and RECEIVER mode of operation. The replicators transition between various phases as enabled by their local FSMs.

### Initialization Phase
In the Initialization phase, both replicators boostrap their local servers in preparation for streaming events based on configuration provided to them. As part of this configuration, they are made aware of the remote endpoints for communication as well as the streams that will participate in replication. 

### Connection Phase
In this phase, the replicators establish a bi-directional communication channel between each other.

### Negotiation Phase
Assuming the Initialization and Connection phases have successfully completed, the replicator in RECEIVER mode proceeds with the Negotiation phase by sending to the TRANSMITTER replicator a request with mappings of the streamOfInterest:lastOffsetProcessed tuples. This provides a hint to the TRANSMITTER where to begin streaming from. 

### Sync Phase (TRANSMITTER)
Sync Phase can be entered on the TRANSMITTER replicator from either the Negotiation Phase in case of the initial bootstrap or resumption from a stoppage or a failure or as iteratively in a loop from a previously completed periodic sync stream. In the case of the initial bootstrap, the TRANSMITTER replicator might need to start from zero offsets and end up starting snapshot transactions on it local streams. In the iterative case, the TRANSMITTER replicator starts from the last streamed offset for every stream of interest as saved by it in its in-memory map of streamOfInterest:lastOffsetProcessed. The case of a resumption from failure is similar to the initial bootstrap case where the TRANSMITTER replicator will start streaming from the RECEIVER supplied offsets received during the Negotiation phase with the difference being the offsets are most likely non-zero. Note that the Sync Phase has 2 sub-phases on the TRANSMITTER namely Fetch Events Phase and Process/Push Events Phase. For the sake of simplicity, collation, batching and other concerns are encapsulated in these 2 sub-phases.

### Sync Phase (RECEIVER)
Sync Phase for the RECEIVER replicator starts in response the stream requests received by it from the TRANSMITTER replicator. The RECEIVER replicator maintains with it the authoritative stream offsets for every event it has successfully processed and applied to its local database. Failure handling for replicators in RECEIVER and TRANSMITTER modes - both brownout and blackout type failures are described in a later section.

### Recovery Phase (TRANSMITTER)
Recovery Phase is a logical-phase and not a real phase as modeled by the Replicator's FSM but it deserves a lucid explanation. A local process failure of the TRANSMITTER replicator due to one of the many reasons (eg. OOM, core-dump, accidental shutdown, power-failure, etc) will result in TRANSMITTER FSM resetting itself back to Initialization Phase. This will have already disrupted its channels with the remote replicators. From this point onwards, the local FSM proceeds with the Initialization->Connection phases until the Negotiation phase is completed and the TRANSMITTER is again made aware of where it is to begin streaming its local streams. At this point, it is again ready to transition to the Sync phase. Note that loss of all local in-memory state on the TRANSMITTER is okay since the Negotiation phase rehydrates this data from the RECEIVER who is the authoritative source of successfully processed stream metadata. It is also important to understand that the TRANSMITTER is designed with idempotency as a key design tenet. 

### Recovery Phase (RECEIVER)
Recovery Phase on the RECEIVER is also a logical phase and proceeds in similar ways as described in the section on TRANSMITTER recovery. On recovery from failure, the Connection phase which terminates with channel establishment, can be initiated and reached from either the TRANSMITTER or RECEIVER replicators depending on the location of failure. Once the RECEIVER reaches the Sync phase, it is business as usual. 

### Drain Phase
In the happy path, the replicator needs to support graceful shutdown and running in any of the modes, needs to transition to draining phase to complete house-keeping tasks and reset its state to init before shutting down.

### Failures
Failure modes and handling them is more involved and will be covered in detail in a subsequent section.

### Transceiver Mode
For channel #3 in the architecture diagram above, the replicator will function in the TRANSCEIVER mode and will be able to serve as both as a TRANSMITTER and a RECEIVER without the need for local process-level separation at each end of the transport channel. Do note that this facility does not subsume the single logical mastership requirement for every replication stream of interest.

## Replicator FSM
<img src="replicator_fsm.png?raw=true" alt="Replicator FSM"/>

## Failure Modes
Since the system is distributed and has many different components, failure handling, detection and recovery need to be individually addressed. Depending on the type of failure, the system maybe able to self-heal without human intervention or might require either a human supervisor or a process supervisor to heal itself.

### Channel Failure

### TRANSMITTER Replicator Failure

### RECEIVER Replicator Failure

### TRANSMITTER/RECEIVER Source Failure

### Site Failure

### Datastore Failure - source and/or sink

## Event Ordering
A total ordering of events is to be expected and provided. Due to the RECEIVER maintaining its local windows of last processed events, any duplicate events streamed to it are dropped by it - at-least-once delivery is acceptable to the RECEIVER. Secondly, the TRANSMITTER and RECEIVER read, stream and replay events in strict order - this does not imply strictly contiguous offsets for the RECEIVER. It does guarantee monotonically increasing offsets received and processed by the RECEIVER. Thirdly, the channel (TCP) is leveraged for event ordering - packets can be reordered in transit but the TCP stack on the RECEIVER deals with buffering and reordering them before presenting them to the replicator. Note that threading the source stream processing needs to guarantee that log event ordering is not mangled on the RECEIVER replicator.

## Assumptions
1. Every replication stream has a single logical mastership. It can have N interested slave receivers.
2. Some level of backwards compatibility needs to spec'ed out both for the data formats handled by the replicator and for the contract between the replicator interfaces themselves.

## Usage Example
### Quick Start
Java 8 and Maven 3.5.0 are required.
```java
// 1. clone the repo
git clone https://github.com/gsharma/replicator.git;

// 2. go to replicator dir
cd replicator;

// 3. run mvn install - compiles and runs integration tests
mvn clean install;

// 4. if everything was successful, you should see this message
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 31.948 s
[INFO] Finished at: 2018-10-23T02:18:58-07:00
[INFO] Final Memory: 32M/337M
```

### Replicator Modes
Replicator can run in one of 3 modes - TRANSMITTER, RECEIVER, TRANSCEIVER. The ReplicationMode is settable in config along with a plethora of other options.

### Configuration Options
There are many knobs available to tweak the replicator's behavior and performance.

### receiver or sender service config
| option                | description                    |
| --------------------- | ------------------------------ |
| serverHost            | service listen host            |
| serverPort            | service listen port            |
| serverThreadCount     | server event-loop thread count |
| workerThreadCount     | server worker thread count     |
| readerIdleTimeSeconds | reader idle timeout            |
| writerIdleTimeSeconds | writer idle timeout            |
| compressionLevel      | wire compression level         |

### overall replicator mode
| option          | description                                   |
| --------------- | --------------------------------------------- |
| ReplicationMode | mode in which the current server is operating |

### local corfu config
| option    | description                                         |
| --------- | --------------------------------------------------- |
| corfuHost | local corfu server host if running in embedded mode |
| corfuPort | local corfu server port if running in embedded mode |

### remote service config
| option           | description                      |
| ---------------- | -------------------------------- |
| remoteServiceUrl | remote replication push endpoint |

### streamer config
| option                     | description                                    |
| -------------------------- | ---------------------------------------------- |
| streamStartOffset          | stream-offset to start/resume streaming from   |
| replicationIntervalSeconds | replication streaming interval                 |
| replicationStreamDepth     | high watermark of replication events to stream |

### Note on Java 8 Usage
Java 8 will be EOL in January 2019. At this time (October 2018), Corfu is using Java 8 and hence the choice of sticking with an older platform. Also note that Corfu's SLF4J dependency resolution is a bit haywire forcing me to fence the enforcer plugin - see the pom.xml if you're interested in details.

