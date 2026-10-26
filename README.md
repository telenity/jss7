# jSS7

Fork of [Mobicents JSS7](https://github.com/RestComm/jss7) 2.0.0.CR2, maintained by Telenity.

SS7 stack for Java (SCTP → M3UA → SCCP → TCAP).

- **Removed obsolete modules**: scheduler, congestion, hardware, ISUP, TestNG dependency
- **Java 8** source/target, **Netty 4** transport, **reload4j** logging

## Documentation

Architecture, M3UA, SCCP, TCAP and the shell command reference are in [docs/](docs/README.md). PDFs: `mvn -Pdocs -pl docs package`.
