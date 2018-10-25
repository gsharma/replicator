# Replicator

## Background
The Replicator is an automaton to provide asynchronous streaming replication between potentially geographically distributed datastore instances. It is a lower-than-application-level data sync-service that can run in one of transmit, receive or transceive modes as a side-car standalone process on either side of a LAN or WAN-based transport. Both the transmitter/streamer and receiver are implemented to use non-blocking I/O over HTTP. The replication protocol as designed, is independent of the choice of the underlying data-store it is implemented on. For the initial phase, the replicator spans [Corfu](https://github.com/CorfuDB/CorfuDB) db clusters spread across multiple remote sites.

## Protocol
### Negotiation Phase
todo::

### Sync Phase
todo::
keep an in-memory map of last streamed offset for every stream of interest
keep a sliding window of processed offsets on receiver side

### Recovery Phase
todo::
Transmitter Failure handling
Receiver Failure handling

## Key Design Concerns

### Do No Evil Modes
The Replicator provides no support for Byzantine fault tolerance. At the moment, the Replicator assumes Fail-Stop mode of operation. It keeps with the design tenet of simplicity and debug-ability.

### Fire-and-Forget Transmitters
todo::

### Transports
Transports are easily pluggable for the most part but practical concerns dictate a deliberate preference towards using HTTP over WAN.

### Replicator FSM
The Replicator itself is trivially an Automaton and wiring for [FSM](https://github.com/gsharma/state-machine) is sprinkled throughout the core service pieces. My FSM implementation is Java9 based and hence temporarily fenced off until such time that the Replicator can be upgraded, as well. The FSM will help with the replication stages - eg. bootstrapping, remote channel establishment, handshaking/negotiation of streams to sync, lifecycle management, etc.

### Backpressure & Feedback Loops
The streamer/sender cannot turn a blind eye to slow or down receivers and appropriately backs off or stops transmission until receiver health improves. Slow start type mechanisms are not implemented but the upper-bound on replication window-size prevents thundering herd type floods against new or just-recovered receivers.

### Concurrency & Throughput
Streamers can be trivially made concurrent with a 1:1 cardinality of stream-worker:stream thereby greatly improving throughput and concurrency. Some simple constraints provide good performance and correctness - always have no more than 1 stream-worker per stream. On the receiver side, a channel pipeline works single threaded thus obviating the need for any reentrant write locks while replaying log events against the remote/receiver's corfu server.

### Transparent Streaming
The Replicator makes no attempt to derive structure or semantic from log events read from the sender's datastore log. This has important implications on concerns like event conflation, deduping, etc with the additional overhead associated with constant up-keep with changing business requirements and domain understanding shoved down Replicator's throat.

### Minimum Dependencies
Since the Replicator has a very specialized job in this universe, there exist no reasons to bloat its dependency hell via 3rd party lib nonsense. Dependency hell is evil and unacceptable.

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

