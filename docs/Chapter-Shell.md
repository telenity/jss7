# Shell Command Reference

The shell is a command line interface for managing a running jSS7 instance: SCTP transport, M3UA and SCCP. This document describes every command as it is **accepted by the current code** (`SCTPShellExecutor`, `M3UAShellExecutor`, `SccpExecutor`).

General rules:

- Arguments are separated by spaces and are positional unless a section says otherwise. A missing argument or unknown subcommand returns `Invalid Command`. An unrecognised value for an enumerated argument (rule type, socket type, traffic mode, parameter name, and so on) returns `Unknown <argument> '<value>'. Expected: <allowed values>`.
- Numeric arguments are parsed as integers; a malformed number returns the parser's exception text.
- `<...>` is a required argument, `[...]` is optional.
- Configuration is applied immediately and persisted by the stack; there is no separate save step.

> [!IMPORTANT]
> This reference was written against the parser code, not against older manuals. Where anything else disagrees, the code and this document win.

Recommended configuration order:

1. SCTP: servers and associations
2. M3UA: application servers, ASPs, routes
3. SCCP: service access points, remote resources, addresses, rules

## Contents

- [Quick Reference](#quick-reference)
- [SCTP Management](#sctp-management)
  - [Create SCTP Server](#create-sctp-server)
  - [Destroy SCTP Server](#destroy-sctp-server)
  - [Start SCTP Server](#start-sctp-server)
  - [Stop SCTP Server](#stop-sctp-server)
  - [Show SCTP Servers](#show-sctp-servers)
  - [Create SCTP Association](#create-sctp-association)
  - [Destroy SCTP Association](#destroy-sctp-association)
  - [Show SCTP Associations](#show-sctp-associations)
- [M3UA Management](#m3ua-management)
  - [Create AS](#create-as)
  - [Destroy AS](#destroy-as)
  - [Add ASP to AS](#add-asp-to-as)
  - [Remove ASP from AS](#remove-asp-from-as)
  - [Show AS](#show-as)
  - [Create ASP](#create-asp)
  - [Destroy ASP](#destroy-asp)
  - [Start ASP](#start-asp)
  - [Stop ASP](#stop-asp)
  - [Show ASP](#show-asp)
  - [Add Route](#add-route)
  - [Remove Route](#remove-route)
  - [Show Routes](#show-routes)
- [SCCP Management](#sccp-management)
  - [SCCP address arguments](#sccp-address-arguments)
  - [Service Access Point Management](#service-access-point-management)
  - [Destination Management](#destination-management)
  - [Remote Signaling Point Management](#remote-signaling-point-management)
  - [Remote Subsystem Management](#remote-subsystem-management)
  - [Address Management](#address-management)
  - [Rule Management](#rule-management)
  - [Long Message Rule Management](#long-message-rule-management)
  - [Concerned Signaling Point Code Management](#concerned-signaling-point-code-management)
  - [General Parameters](#general-parameters)
- [Example 1: Single Test Gateway](#example-1-single-test-gateway)
- [Example 2: Dual Gateway to a Mated STP Pair](#example-2-dual-gateway-to-a-mated-stp-pair)

---

---

## Quick Reference

| Command | Syntax |
|---------|--------|
| **SCTP** | |
| server create | `sctp server create <server-name> <host-ip> <host-port> [<socket-type>]` |
| server destroy / start / stop | `sctp server destroy <server-name>` |
| server show | `sctp server show` |
| association create (client) | `sctp association create <assoc-name> CLIENT <peer-ip> <peer-port> <host-ip> <host-port> [<socket-type>]` |
| association create (server) | `sctp association create <assoc-name> SERVER <server-name> <peer-ip> <peer-port> [<socket-type>]` |
| association destroy | `sctp association destroy <assoc-name>` |
| association show | `sctp association show` |
| **M3UA** | |
| as create | `m3ua as create <as-name> <functionality> [mode <SE or DE>] [ipspType <CLIENT or SERVER>] [rc <n>] [traffic-mode <mode>] [min-asp <n>] [network-appearance <n>]` |
| as destroy | `m3ua as destroy <as-name>` |
| as add / remove | `m3ua as add <as-name> <asp-name>` |
| as show | `m3ua as show` |
| asp create | `m3ua asp create <asp-name> <sctp-association> [aspid <n>] [heartbeat <true or false>]` |
| asp destroy / start / stop | `m3ua asp start <asp-name>` |
| asp show | `m3ua asp show` |
| route add / remove | `m3ua route add <as-name> <dpc> [<opc> <si>]` |
| route show | `m3ua route show` |
| **SCCP** | |
| sap create / modify | `sccp sap create <id> <mtp3-id> <opc> <ni>` |
| sap delete / show | `sccp sap delete <id>`, `sccp sap show [<id>]` |
| dest create / modify | `sccp dest create <sap-id> <id> <first-dpc> <last-dpc> <first-sls> <last-sls> <sls-mask>` |
| dest delete / show | `sccp dest delete <sap-id> <id>`, `sccp dest show <sap-id> [<id>]` |
| rsp create / modify | `sccp rsp create <id> <remote-spc> <rspc-flag> <mask>` |
| rsp delete / show | `sccp rsp delete <id>`, `sccp rsp show [<id>]` |
| rss create / modify | `sccp rss create <id> <remote-spc> <remote-ssn> <rss-flag> [prohibitedWhenSpcResuming]` |
| rss delete / show | `sccp rss delete <id>`, `sccp rss show [<id>]` |
| primary_add / backup_add create / modify | `sccp primary_add create <id> <ai> <pc> <ssn> <tt> <np> <nai> <digits>` |
| primary_add / backup_add delete / show | `sccp primary_add delete <id>`, `sccp backup_add show [<id>]` |
| rule create / modify | `sccp rule create <id> <mask> <ai> <pc> <ssn> <tt> <np> <nai> <digits> <rule-type> <primary-address-id> [<backup-address-id>] [<loadsharing-algorithm>] [<sgls-backup-id>...] [<origination-type>]` |
| rule delete / show | `sccp rule delete <id>`, `sccp rule show [<id>]` |
| lmr create / modify | `sccp lmr create <id> <first-spc> <last-spc> <long-message-rule-type>` |
| lmr delete / show | `sccp lmr delete <id>`, `sccp lmr show [<id>]` |
| csp create / modify | `sccp csp create <id> <spc>` |
| csp delete / show | `sccp csp delete <id>`, `sccp csp show [<id>]` |
| set / get | `sccp set <parameter-name> <parameter-value>`, `sccp get [<parameter-name>]` |

*Optional arguments are in `[...]`; `destroy`, `start`, `stop`, `modify` and `remove` take the same arguments as the command shown on their row.*

---

## SCTP Management

SCTP transport is managed with the `sctp` command. It allows the following:

```
sctp server create
sctp server destroy
sctp server start
sctp server stop
sctp server show
sctp association create
sctp association destroy
sctp association show
```

### Create SCTP Server

An SCTP server can be created by issuing a command with the following structure:

```
sctp server create <server-name> <host-ip> <host-port> [<socket-type>]
```

Where:

- `<server-name>` Unique name assigned to the server.
- `<host-ip>` The IP address the underlying server socket binds to. For SCTP multi-homing, pass comma-separated addresses **without spaces**; the first is the primary address, the rest are secondary addresses. If the primary address becomes unavailable the association falls back to a secondary one. Multi-homing is an SCTP feature; with `TCP` pass a single address.
- `<host-port>` The port the underlying server socket binds to.
- `<socket-type>` **Optional.** `SCTP` (**default**) or `TCP`, case-insensitive.

Examples:

```
sctp server create TestServer 127.0.0.1 2905
Successfully added Server=TestServer

sctp server create TestServer 10.2.50.145,10.2.50.146 2905
Successfully added Server=TestServer

sctp server create TestServerTCP 10.2.50.145 2906 TCP
Successfully added Server=TestServerTCP
```

### Destroy SCTP Server

```
sctp server destroy <server-name>
```

Where:

- `<server-name>` Name of the server to destroy. Stop the server first.

```
sctp server destroy TestServer
Successfully removed Server=TestServer
```

### Start SCTP Server

```
sctp server start <server-name>
```

Where:

- `<server-name>` Name of the server to start. The server socket is bound to the ip:port given at creation time.

```
sctp server start TestServer
Successfully started Server=TestServer
```

### Stop SCTP Server

```
sctp server stop <server-name>
```

Where:

- `<server-name>` Name of the server to stop. The socket is closed and all resources released.

```
sctp server stop TestServer
Successfully stopped Server=TestServer
```

### Show SCTP Servers

```
sctp server show
```

Lists every server with its type, name, started flag, bound address and the associations attached to it. Returns `No Server defined yet` if none exist.

### Create SCTP Association

An association can be created by issuing a command with one of the following structures, depending on which side initiates the connection:

```
sctp association create <assoc-name> CLIENT <peer-ip> <peer-port> <host-ip> <host-port> [<socket-type>]
sctp association create <assoc-name> SERVER <server-name> <peer-ip> <peer-port> [<socket-type>]
```

Where:

- `<assoc-name>` Unique name of the association.
- `CLIENT | SERVER` Whether this side is the client or the server. *Case-insensitive.*
  - `CLIENT`: this side initiates the connection. It binds to `<host-ip>:<host-port>` and connects to `<peer-ip>:<peer-port>`. `<host-ip>` accepts comma-separated addresses for multi-homing, as for servers.
  - `SERVER`: this side waits for the peer to connect. The connection is accepted from `<peer-ip>:<peer-port>`. `<server-name>` names an existing SCTP server that owns this association; no host ip/port is given because the server already has them.
- `<socket-type>` **Optional.** `SCTP` (**default**) or `TCP`. For a `SERVER` association it must match the type of the server.

> [!NOTE]
> Associations have no start/stop commands of their own. They are started and stopped by the M3UA ASP that uses them (see `m3ua asp start`).

Examples:

```
sctp association create Assoc1 CLIENT 192.168.56.101 2905 192.168.56.1,192.168.56.2 2905
Successfully added client Association=Assoc1

sctp association create Assoc2 SERVER TestServer 192.168.56.101 2905
Successfully added server Association=Assoc2
```

### Destroy SCTP Association

```
sctp association destroy <assoc-name>
```

Where:

- `<assoc-name>` Name of the association to destroy. It must not be in use by a started ASP.

```
sctp association destroy Assoc1
Successfully removed Association=Assoc1
```

### Show SCTP Associations

```
sctp association show
```

Lists every association with its type, name, started flag, peer and host addresses, and for `SERVER` associations the owning server. Returns `No Association defined yet` if none exist.

---

## M3UA Management

M3UA is managed with the `m3ua` command. It allows the following:

```
m3ua as create
m3ua as destroy
m3ua as add
m3ua as remove
m3ua as show
m3ua asp create
m3ua asp destroy
m3ua asp start
m3ua asp stop
m3ua asp show
m3ua route add
m3ua route remove
m3ua route show
```

### Create AS

An Application Server (AS) can be created by issuing a command with the following structure:

```
m3ua as create <as-name> <AS | SGW | IPSP> [mode <SE | DE>] [ipspType <CLIENT | SERVER>] [rc <routing-context>] [traffic-mode <loadshare | override | broadcast>] [min-asp <n>] [network-appearance <na>]
```

The two leading arguments are positional. Everything after them is a list of `key value` pairs that may appear in any order. An unknown key returns `Unknown option '<key>'. Expected: mode, ipspType, rc, traffic-mode, network-appearance, min-asp`.

Where:

- `<as-name>` Simple string that identifies the AS. Must be unique. **Mandatory.**
- `AS | SGW | IPSP` Functionality of the **local** side. *Case-sensitive.* **Mandatory.**
- `mode <SE | DE>` Single or Double Exchange of ASP State Maintenance (ASPSM) and ASP Traffic Maintenance (ASPTM) messages. *Case-sensitive.*
- `ipspType <CLIENT | SERVER>` For `IPSP` functionality, whether this side acts as the client or the server. *Case-insensitive.*
- `rc <routing-context>` Routing Context, a single integer. **Optional.** If an ASP is assigned to more than one AS over the same SCTP association, every such AS must have a unique Routing Context; otherwise incoming messages without RC are dropped with an Error message.
- `traffic-mode <loadshare | override | broadcast>` Traffic mode for the ASPs of this AS. *Lowercase.* **Default** is `loadshare`.
- `min-asp <n>` Minimum number of active ASPs before payload starts flowing in `loadshare` mode. **Optional**, **default** `1`. Has no effect in other traffic modes.
- `network-appearance <na>` Network Appearance, an integer shared by SG and AS that identifies the SS7 network when several networks share one SCTP association. **Optional.**

Examples:

```
m3ua as create AS1 IPSP mode DE ipspType server rc 1 traffic-mode loadshare
Successfully created AS name=AS1

m3ua as create AS2 AS mode SE rc 100 traffic-mode loadshare network-appearance 12
Successfully created AS name=AS2
```

### Destroy AS

```
m3ua as destroy <as-name>
```

Where:

- `<as-name>` Name of the AS. All ASPs must be unassigned from it and it must not be used in any route, otherwise an error naming the blocking ASP or route is returned.

```
m3ua as destroy AS1
Successfully destroyed AS name=AS1
```

### Add ASP to AS

```
m3ua as add <as-name> <asp-name>
```

Where:

- `<as-name>` Name of an AS created earlier.
- `<asp-name>` Name of an ASP created earlier.

An ASP can be assigned to more than one AS over a single SCTP association, provided every one of those ASs has a unique Routing Context and they agree on functionality, exchange mode and IPSP type. Violations are reported with a descriptive error.

```
m3ua as add AS1 ASP1
Successfully added ASP name=ASP1 to AS name=AS1
```

### Remove ASP from AS

```
m3ua as remove <as-name> <asp-name>
```

```
m3ua as remove AS1 ASP1
Successfully removed ASP name=ASP1 from AS name=AS1
```

### Show AS

```
m3ua as show
```

Lists every AS with its functionality, mode, IPSP type, routing context, traffic mode, network appearance, FSM states and assigned ASPs. Returns `No AS defined yet` if none exist.

### Create ASP

An Application Server Process (ASP) can be created by issuing a command with the following structure:

```
m3ua asp create <asp-name> <sctp-association> [aspid <aspid>] [heartbeat <true | false>]
```

The two leading arguments are positional; `aspid` and `heartbeat` are `key value` pairs in any order.

Where:

- `<asp-name>` Unique name of this ASP.
- `<sctp-association>` Name of an existing SCTP association. The association must not be started or already bound to another ASP.
- `aspid <aspid>` ASP Identifier sent in ASP Up. **Optional.** If omitted, the next available id is generated. An id already used by another ASP is rejected.
- `heartbeat <true | false>` Enables the M3UA heartbeat (BEAT) mechanism between peers. **Optional**, **default** `false`. When enabled a BEAT is sent every 5 seconds by default; after more than two consecutive missed acknowledgements the underlying association is stopped and started again.

```
m3ua asp create ASP1 Assoc1 aspid 12 heartbeat true
Successfully created ASP name=ASP1
```

### Destroy ASP

```
m3ua asp destroy <asp-name>
```

Where:

- `<asp-name>` Name of the ASP. Stop it and unassign it from every AS first.

```
m3ua asp destroy ASP1
Successfully destroyed ASP name=ASP1
```

### Start ASP

```
m3ua asp start <asp-name>
```

Where:

- `<asp-name>` Name of an ASP created earlier. It must be assigned to at least one AS. Starting the ASP starts its SCTP association.

```
m3ua asp start ASP1
Successfully started ASP name=ASP1
```

### Stop ASP

```
m3ua asp stop <asp-name>
```

```
m3ua asp stop ASP1
Successfully stopped ASP name=ASP1
```

### Show ASP

```
m3ua asp show
```

Lists every ASP with its association, aspid, heartbeat flag, started flag and the ASs it is assigned to. Returns `No ASP defined yet` if none exist.

### Add Route

Configures which AS handles traffic for a destination point code:

```
m3ua route add <as-name> <dpc> [<opc> <si>]
```

Where:

- `<as-name>` Name of an AS created earlier.
- `<dpc>` Destination point code.
- `<opc>` Originating point code. `-1` means any.
- `<si>` Service indicator. `-1` means any.

> [!WARNING]
> `<opc>` and `<si>` must be given together or both omitted. Omitting them is the same as passing `-1 -1`. Passing only `<opc>` returns `Invalid Command`.

At runtime the route is looked up in this order: exact `(dpc, opc, si)`, then `(dpc, opc, any)`, then `(dpc, any, any)`. Several ASs may be added to the same route key (default limit 2, configurable programmatically); traffic is loadshared across them.

```
m3ua route add AS1 2
AS=AS1 successfully added as route for DPC=2

m3ua route add AS1 2 -1 -1
AS=AS1 successfully added as route for DPC=2

m3ua route add AS2 2 1 3
AS=AS2 successfully added as route for DPC=2
```

### Remove Route

```
m3ua route remove <as-name> <dpc> [<opc> <si>]
```

The arguments have the same meaning as for `m3ua route add` and must match the key the route was added with.

```
m3ua route remove AS1 2 -1 -1
Successfully removed AS=AS1 as route for DPC=2
```

### Show Routes

```
m3ua route show
```

Prints each route key as `dpc:opc:si` followed by the AS names bound to it. Returns `No Route defined yet` if none exist.

```
m3ua route show
2:-1:-1        AS1,AS2,
```

---

## SCCP Management

SCCP provides connectionless and connection-oriented network services, including global title translation (GTT), routing, segmentation and reassembly. It is the transport layer for TCAP-based services.

Configuration steps:

1. **Service access points** (`sccp sap`) and their **destinations** (`sccp dest`). **Mandatory.** A sap is the logical definition of one Mtp3UserPart: local point code, network indicator and the set of remote point codes reachable through it.
2. **Remote signaling point codes** (`sccp rsp`) and **remote subsystems** (`sccp rss`). **Mandatory.** Remote subsystems are not required when routing only on global title.
3. **Primary and backup addresses** (`sccp primary_add`, `sccp backup_add`) and **rules** (`sccp rule`) for routing.
4. **Long message rules** (`sccp lmr`). **Optional.** Without them only UDT messages are used.
5. **Concerned signaling point codes** (`sccp csp`). **Optional.** Each is notified when a local SCCP user becomes available or unavailable.
6. **General parameters** (`sccp set`, `sccp get`). **Optional.**

### SCCP address arguments

The `primary_add`, `backup_add` and `rule` commands take an SCCP address as **seven positional arguments**, always in this order and always all present:

```
<address-indicator> <point-code> <subsystem-number> <translation-type> <numbering-plan> <nature-of-address-indicator> <digits>
```

Where:

- `<address-indicator>` One octet describing which elements are present in the address and how it is routed:

  | Bit | Meaning |
  |-----|---------|
  | 0 | Point code present |
  | 1 | Subsystem number present |
  | 2-5 | Global Title Indicator (GTI) |
  | 6 | Routing indicator: 0 route on GT, 1 route on DPC+SSN |

  Common values: `16` GTI 4, route on GT, no PC/SSN. `18` GTI 4, route on GT, SSN present. `67` route on DPC+SSN, PC and SSN present, no GT. `71` route on DPC+SSN, PC and SSN present, GTI 1.

- `<point-code>` Point code. If bit 0 of the address indicator is set this must not be `0`, otherwise an error is returned. Ignored when bit 0 is not set.
- `<subsystem-number>` Subsystem number. If bit 1 of the address indicator is set this must not be `0`. Ignored when bit 1 is not set.
- `<translation-type>` Translation type. Ignored when GTI is 0 or 1.

  | Value | Description |
  |-------|-------------|
  | 0 | Unknown |
  | 1-63 | International service |
  | 64-127 | Spare |
  | 128-254 | National network specific |
  | 255 | Reserved for expansion |

- `<numbering-plan>` Ignored when GTI is 0, 1 or 2.

  | Value | Description |
  |-------|-------------|
  | 0 | Unknown |
  | 1 | ISDN/Telephony (E.163, E.164) |
  | 2 | Generic |
  | 3 | Data (X.121) |
  | 4 | Telex (F.69) |
  | 5 | Maritime mobile (E.210, E.211) |
  | 6 | Land mobile (E.212) |
  | 7 | ISDN/Mobile (E.214) |
  | 14 | Private network |
  | 15 | Reserved |

- `<nature-of-address-indicator>` Used only when GTI is 1 or 4.

  | Value | Description |
  |-------|-------------|
  | 0 | Unknown |
  | 1 | Subscriber number |
  | 2 | Reserved for national use |
  | 3 | National significant number |
  | 4 | International number |

- `<digits>` Global title digits. In a rule this is the pattern to match; in an address it is the translation target. May be split into sections with `/`. See the `mask` description under `sccp rule create` for how sections are used.

  | Value | Meaning |
  |-------|---------|
  | `-` | Padding. In an address: append nothing for this section. |
  | `*` | Wildcard (rule pattern): any number of digits. |
  | `?` | Wildcard (rule pattern): exactly one digit. |
  | `/` | Section separator. |

> [!TIP]
> Arguments that are not used for the chosen address indicator must still be present; write `0`.

### Service Access Point Management

```
sccp sap create
sccp sap modify
sccp sap delete
sccp sap show
```

#### Create Service Access Point

```
sccp sap create <id> <mtp3-id> <opc> <ni>
```

Where:

- `<id>` Unique number identifying this sap.
- `<mtp3-id>` Index of the Mtp3UserPart (M3UA management) this sap is bound to. Configure one sap per Mtp3UserPart.
- `<opc>` Local signaling point code.
- `<ni>` Network indicator that forms part of the service information octet (SIO). Typically `0` international, `2` national.

For each sap one or more destinations must be configured with `sccp dest create`.

```
sccp sap create 1 1 101 2
Service access point successfully added

sccp sap create 2 2 102 2
Service access point successfully added
```

#### Modify Service Access Point

```
sccp sap modify <id> <mtp3-id> <opc> <ni>
```

The arguments have the same meaning as for `create`.

#### Delete Service Access Point

```
sccp sap delete <id>
```

```
sccp sap delete 1
Service access point successfully deleted
```

#### Show Service Access Point

```
sccp sap show [<id>]
```

`<id>` is optional. If given, only that sap is shown; otherwise all saps are shown.

### Destination Management

```
sccp dest create
sccp dest modify
sccp dest delete
sccp dest show
```

#### Create Destination

```
sccp dest create <sap-id> <id> <first-dpc> <last-dpc> <first-sls> <last-sls> <sls-mask>
```

Where:

- `<sap-id>` Id of the sap this destination belongs to.
- `<id>` Number identifying this destination. Unique within the sap.
- `<first-dpc>` First remote point code of the range.
- `<last-dpc>` Last remote point code of the range. Equal to `<first-dpc>` for a single point code.
- `<first-sls>` First SLS value of the range.
- `<last-sls>` Last SLS value of the range.
- `<sls-mask>` The SLS of a message is ANDed with this mask before comparing with the SLS range.

SLS values range from 0 to 255. To cover all SLS values use `0 255 255`.

```
sccp dest create 1 1 201 201 0 7 7
Destination definition successfully added

sccp dest create 2 1 300 399 0 255 255
Destination definition successfully added
```

#### Modify Destination

```
sccp dest modify <sap-id> <id> <first-dpc> <last-dpc> <first-sls> <last-sls> <sls-mask>
```

#### Delete Destination

```
sccp dest delete <sap-id> <id>
```

```
sccp dest delete 1 1
Destination definition successfully deleted
```

#### Show Destination

```
sccp dest show <sap-id> [<id>]
```

`<sap-id>` is required. If `<id>` is given only that destination is shown; otherwise the sap and all its destinations are shown.

### Remote Signaling Point Management

Every remote signaling point SCCP communicates with must be configured.

```
sccp rsp create
sccp rsp modify
sccp rsp delete
sccp rsp show
```

#### Create Remote Signaling Point

```
sccp rsp create <id> <remote-spc> <rspc-flag> <mask>
```

Where:

- `<id>` Unique number identifying this remote signaling point.
- `<remote-spc>` The remote signaling point code.
- `<rspc-flag>` *Reserved, not used.* Pass `0`.
- `<mask>` *Reserved, not used.* Pass `0`.

```
sccp rsp create 1 6477 0 0
Remote Signaling Pointcode successfully added
```

#### Modify Remote Signaling Point

```
sccp rsp modify <id> <remote-spc> <rspc-flag> <mask>
```

#### Delete Remote Signaling Point

```
sccp rsp delete <id>
```

```
sccp rsp delete 1
Remote Signaling Pointcode successfully deleted
```

#### Show Remote Signaling Point

```
sccp rsp show [<id>]
```

### Remote Subsystem Management

Every remote subsystem SCCP communicates with must be configured. Configure the remote signaling point first.

```
sccp rss create
sccp rss modify
sccp rss delete
sccp rss show
```

#### Create Remote Subsystem

```
sccp rss create <id> <remote-spc> <remote-ssn> <rss-flag> [prohibitedWhenSpcResuming]
```

Where:

- `<id>` Unique number identifying this remote subsystem.
- `<remote-spc>` Remote signaling point code where the subsystem is deployed.
- `<remote-ssn>` Remote subsystem number.
- `<rss-flag>` *Reserved, not used.* Pass `0`.
- `prohibitedWhenSpcResuming` **Optional**, case-insensitive. When present, the subsystem is marked prohibited when its signaling point code resumes, until a Subsystem Allowed is received. Any other value in this position is rejected.

```
sccp rss create 1 6477 8 0
Remote Subsystem successfully added

sccp rss create 2 6477 6 0 prohibitedWhenSpcResuming
Remote Subsystem successfully added
```

#### Modify Remote Subsystem

```
sccp rss modify <id> <remote-spc> <remote-ssn> <rss-flag> [prohibitedWhenSpcResuming]
```

#### Delete Remote Subsystem

```
sccp rss delete <id>
```

```
sccp rss delete 1
Remote Subsystem successfully deleted
```

#### Show Remote Subsystem

```
sccp rss show [<id>]
```

### Address Management

Primary and backup addresses are the translation targets referenced by rules. The digits of the address are combined with the digits being translated according to the rule's mask. Primary and backup addresses live in separate tables with separate ids.

```
sccp primary_add create
sccp primary_add modify
sccp primary_add delete
sccp primary_add show
sccp backup_add create
sccp backup_add modify
sccp backup_add delete
sccp backup_add show
```

#### Create Address

```
sccp primary_add create <id> <address-indicator> <point-code> <subsystem-number> <translation-type> <numbering-plan> <nature-of-address-indicator> <digits>
sccp backup_add create <id> <address-indicator> <point-code> <subsystem-number> <translation-type> <numbering-plan> <nature-of-address-indicator> <digits>
```

Where:

- `<id>` Unique number identifying this address within its table (primary or backup).
- The remaining arguments are described under **SCCP address arguments**. In `<digits>`, a section written as `-` contributes nothing to the translated digits even when the rule mask says `R` for that section.

```
sccp primary_add create 1 71 2 8 0 0 3 123456789
Address successfully added

sccp backup_add create 1 71 3 8 0 0 3 123456789
Address successfully added

sccp primary_add create 2 67 2 8 0 0 0 -
Address successfully added
```

#### Modify Address

```
sccp primary_add modify <id> <address-indicator> <point-code> <subsystem-number> <translation-type> <numbering-plan> <nature-of-address-indicator> <digits>
sccp backup_add modify <id> <address-indicator> <point-code> <subsystem-number> <translation-type> <numbering-plan> <nature-of-address-indicator> <digits>
```

#### Delete Address

```
sccp primary_add delete <id>
sccp backup_add delete <id>
```

```
sccp primary_add delete 1
Address successfully deleted
```

#### Show Address

```
sccp primary_add show [<id>]
sccp backup_add show [<id>]
```

### Rule Management

```
sccp rule create
sccp rule modify
sccp rule delete
sccp rule show
```

#### Create Rule

> [!TIP]
> Configure primary (and backup) addresses before creating rules. A rule that references a missing address is rejected.

```
sccp rule create <id> <mask> <address-indicator> <point-code> <subsystem-number> <translation-type> <numbering-plan> <nature-of-address-indicator> <digits> <rule-type> <primary-address-id> [<backup-address-id>] [<loadsharing-algorithm>] [<sgls-backup-id>...] [<origination-type>]
```

All arguments are positional. Which of the optional trailing arguments are present depends on `<rule-type>`; see the templates below.

Where:

- `<id>` Unique number identifying this rule.
- `<mask>` Defines which parts of the dialed digits are kept and which are replaced by digits from the primary or backup address. The mask is split into sections with `/`; the number of sections must equal the number of sections in the rule's `<digits>` and in the address's `<digits>`.

  | Mnemonic | Function |
  |----------|----------|
  | `K` | Keep the dialed digits of this section in the translated digits. |
  | `R` | Replace the dialed digits of this section with the same section from the primary or backup address. |
  | `/` | Section separator. |

- `<address-indicator>` ... `<digits>` The pattern to match, as described under **SCCP address arguments**. `<digits>` may use the wildcards `*` and `?`.
- `<rule-type>` *Case-insensitive.*

  | Value | Description |
  |-------|-------------|
  | `Solitary` | Only the primary address is used. No backup address is given. |
  | `Dominant` | Primary and backup addresses are both mandatory. The primary is used whenever it is available. |
  | `Loadshared` | Primary and backup addresses are both mandatory. Traffic is split between them according to `<loadsharing-algorithm>`. |
  | `Sgls` | Primary and backup addresses are both mandatory; any number of further backup addresses may follow. Every destination that is currently available is put in a list and the message is sent to entry `SLS mod count`, so traffic spreads evenly over all reachable destinations. |

- `<primary-address-id>` Id of a `primary_add` entry.
- `<backup-address-id>` Id of a `backup_add` entry. **Required** for every rule type except `Solitary`; the address must already exist, otherwise `No backup address defined for id=N` is returned.
- `<loadsharing-algorithm>` **Required** for `Loadshared` only. *Case-insensitive.*

  | Value | Description |
  |-------|-------------|
  | `Bit0` | `(SLS & 0x01) == 0` routes to primary, else to backup. |
  | `Bit1` | `(SLS & 0x02) == 0` routes to primary, else to backup. |
  | `Bit2` | `(SLS & 0x04) == 0` routes to primary, else to backup. |
  | `Bit3` | `(SLS & 0x08) == 0` routes to primary, else to backup. |
  | `Bit4` | `(SLS & 0x10) == 0` routes to primary, else to backup. |

  > [!WARNING]
  > Locally originated TCAP class 1 traffic uses SLS values 0 to 15 (`maxSeqControl` default 15), so `Bit4` is always 0 for it and all local traffic goes to the primary. Use `Bit0` to split local traffic evenly.

- `<sgls-backup-id>...` `Sgls` only. Zero or more additional `backup_add` ids to loadshare over, on top of the primary and backup address. Each must exist and match the mask sections.
- `<origination-type>` **Optional**, case-insensitive. `LocalOriginated` applies the rule only to messages from local SCCP users (for example a local TCAP stack). `RemoteOriginated` applies it only to messages from the SS7 network. When absent the rule applies to all messages.

Templates per rule type. The argument after those the rule type takes is read as `<origination-type>`, so a leftover value there (for example `-1` after a `Solitary` primary id) returns `Wrong value: '<value>'`; any argument after the origination type returns `Invalid Command`. For `Sgls` every numeric argument after `<backup-address-id>` is read as an `<sgls-backup-id>`, so do not put a placeholder there.

```
sccp rule create <id> <mask> <7 address args> Solitary   <primary-address-id> [<origination-type>]
sccp rule create <id> <mask> <7 address args> Dominant   <primary-address-id> <backup-address-id> [<origination-type>]
sccp rule create <id> <mask> <7 address args> Loadshared <primary-address-id> <backup-address-id> <loadsharing-algorithm> [<origination-type>]
sccp rule create <id> <mask> <7 address args> Sgls       <primary-address-id> <backup-address-id> [<sgls-backup-id>...] [<origination-type>]
```

Rule ordering: when a message is translated, rules are evaluated from the top of a sorted list. Rules with `LocalOriginated` or `RemoteOriginated` come before rules with origination type `All`. Within the same origination type, patterns without wildcards come first (shortest first), then patterns with `?`, then patterns with `*`. Among patterns with `?`, fewer wildcards rank higher, and for an equal count the pattern whose first `?` appears later ranks higher.

Examples:

```
sccp rule create 1 R 71 2 8 0 0 3 123456789 Solitary 1
Rule successfully added

sccp rule create 2 R 71 2 8 0 0 3 123456789 Dominant 1 1
Rule successfully added

sccp rule create 3 R 71 2 8 0 0 3 123456789 Loadshared 1 1 Bit4
Rule successfully added

sccp rule create 4 K 16 0 0 0 1 4 375259859986 Solitary 1 RemoteOriginated
Rule successfully added

sccp rule create 5 K/R 16 0 0 0 1 4 90/* Dominant 1 1 LocalOriginated
Rule successfully added

sccp rule create 6 R 71 2 8 0 0 3 123456789 Sgls 1 1 2 3
Rule successfully added
```

> [!WARNING]
> A frequent mistake is to place the origination type before the rule type. The rule type must be the first argument after the seven address arguments:

```
sccp rule modify 5 K 16 0 0 0 1 4 375259859986 RemoteOriginated 1 -1 Solitary
Unknown rule type 'RemoteOriginated'. Expected: Solitary, Dominant, Loadshared, Sgls

sccp rule modify 5 K 16 0 0 0 1 4 375259859986 Solitary 1 RemoteOriginated
Rule successfully modified
```

#### Modify Rule

```
sccp rule modify <id> <mask> <address-indicator> <point-code> <subsystem-number> <translation-type> <numbering-plan> <nature-of-address-indicator> <digits> <rule-type> <primary-address-id> [<backup-address-id>] [<loadsharing-algorithm>] [<origination-type>]
```

The arguments have the same meaning and positions as for `create`.

#### Delete Rule

```
sccp rule delete <id>
```

```
sccp rule delete 1
Rule successfully removed
```

#### Show Rule

```
sccp rule show [<id>]
```

`<id>` is optional. If given only that rule is shown; otherwise all rules are shown.

### Long Message Rule Management

Long message rules define which message type (UDT, XUDT or LUDT) is used for outgoing messages depending on the destination point code. Without any long message rule only UDT is used.

```
sccp lmr create
sccp lmr modify
sccp lmr delete
sccp lmr show
```

#### Create Long Message Rule

```
sccp lmr create <id> <first-spc> <last-spc> <long-message-rule-type>
```

Where:

- `<id>` Unique number identifying this rule.
- `<first-spc>` First remote point code of the range the rule applies to.
- `<last-spc>` Last remote point code of the range. Equal to `<first-spc>` for a single point code.
- `<long-message-rule-type>` *Lowercase.* `udt` (long messages forbidden), `xudt`, `ludt`, or `ludt_segm` (LUDT with segmentation field).

```
sccp lmr create 1 201 201 xudt
Long message rule successfully added

sccp lmr create 2 230 239 udt
Long message rule successfully added
```

#### Modify Long Message Rule

```
sccp lmr modify <id> <first-spc> <last-spc> <long-message-rule-type>
```

#### Delete Long Message Rule

```
sccp lmr delete <id>
```

```
sccp lmr delete 1
Long message rule successfully deleted
```

#### Show Long Message Rule

```
sccp lmr show [<id>]
```

### Concerned Signaling Point Code Management

Concerned signaling point codes are notified with SSA when a local subsystem registers and with SSP when it unregisters.

```
sccp csp create
sccp csp modify
sccp csp delete
sccp csp show
```

#### Create Concerned Signaling Point Code

```
sccp csp create <id> <spc>
```

Where:

- `<id>` Unique number identifying this entry.
- `<spc>` Remote signaling point code to notify.

```
sccp csp create 1 201
Concerned spc successfully added

sccp csp create 2 202
Concerned spc successfully added
```

#### Modify Concerned Signaling Point Code

```
sccp csp modify <id> <spc>
```

Returns `Concerned spc doesn't exist` if `<id>` is unknown.

#### Delete Concerned Signaling Point Code

```
sccp csp delete <id>
```

```
sccp csp delete 1
Concerned spc successfully deleted
```

#### Show Concerned Signaling Point Code

```
sccp csp show [<id>]
```

### General Parameters

General parameters affect the whole SCCP stack.

> [!NOTE]
> Values outside the range are clamped to the nearest bound and the command still reports success.

| Name | Function | Range | Default |
|------|----------|-------|---------|
| `zMarginXudtMessage` | XUDT messages with data longer than this are segmented. | 160 - 255 | 240 |
| `reassemblyTimerDelay` | Reassembly timeout for segmented messages, in milliseconds. | 10000 - 20000 | 15000 |
| `maxDataMessage` | Maximum SCCP message data length for all message types. | 2560 - 3952 | 2560 |
| `removeSpc` | After GTT, if the address carries a point code but the address indicator says route on GT, remove the point code before sending to MTP3. Applies to both called and calling party addresses. | `true` / `false` | `true` |
| `sstTimerDuration_Min` | Initial SST sending interval, in milliseconds. | 5000 - 10000 | 10000 |
| `sstTimerDuration_Max` | Maximum SST sending interval after increases, in milliseconds. | 600000 - 1200000 | 600000 |
| `sstTimerDuration_IncreaseFactor` | Multiplier applied to the SST interval on each retry. Double. | 1 - 4 | 1.5 |

#### Set Parameter

```
sccp set <parameter-name> <parameter-value>
```

`<parameter-name>` is case-insensitive.

```
sccp set zMarginXudtMessage 230
Parameter has been successfully set

sccp set removeSpc false
Parameter has been successfully set
```

#### Get Parameter

```
sccp get [<parameter-name>]
```

If `<parameter-name>` is omitted, all parameters are listed.

```
sccp get zMarginXudtMessage
zMarginXudtMessage = 230
```

---

## Example 1: Single Test Gateway

A single test gateway, *tgw*, connects to one STP (point code 3001) over one multi-homed SCTP association. The gateway uses point code 4000 and global title 15550004000.

### SCTP, M3UA and SCCP

Only the STP's primary IP is given; its secondary address is learned from the SCTP INIT ACK. The gateway binds to two local addresses.

```
sctp association create Assoc1 CLIENT 198.51.100.30 2905 192.0.2.50,192.0.2.150 2905

m3ua as create AS1 IPSP mode SE ipspType CLIENT traffic-mode loadshare
m3ua asp create ASP1 Assoc1
m3ua as add AS1 ASP1
m3ua asp start ASP1
m3ua route add AS1 3001 -1 -1

sccp sap create 1 1 4000 3
sccp dest create 1 1 3001 3001 0 255 255
sccp rsp create 1 3001 0 0
sccp csp create 1 3001
```

### Global Title Translation

Rule 1 delivers traffic from the network for the gateway's own GT on SSN 8 to the local SCCP user. Rule 2 sends all locally originated traffic to the STP on GT. See Example 2 for the reasoning behind the address indicators and origination types.

```
sccp primary_add create 1 17 4000 0 0 1 4 15550004000
sccp rule create 1 K 18 0 8 0 1 4 15550004000 Solitary 1 RemoteOriginated

sccp primary_add create 2 17 3001 0 0 1 4 -
sccp rule create 2 K 16 0 0 0 1 4 * Solitary 2 LocalOriginated
```

## Example 2: Dual Gateway to a Mated STP Pair

Two gateways, *gw1* and *gw2*, each connect to a mated STP pair (point codes 1001 and 1002) over two multi-homed SCTP associations. Both gateways use point code 2000 and global title 15550001000. Each gateway has its own configuration, so every command is run on both.

### SCTP

Only the STP's primary IP is given; its secondary address is learned from the SCTP INIT ACK. The gateway binds to two local addresses.

```
# gw1
sctp association create Assoc1 CLIENT 198.51.100.10 2905 192.0.2.160,192.0.2.220 2905
sctp association create Assoc2 CLIENT 198.51.100.20 2905 192.0.2.160,192.0.2.220 2906
# gw2
sctp association create Assoc1 CLIENT 198.51.100.10 2905 192.0.2.161,192.0.2.221 2905
sctp association create Assoc2 CLIENT 198.51.100.20 2905 192.0.2.161,192.0.2.221 2906
```

### M3UA and SCCP

One IPSP client AS and ASP per STP, one route per STP, then the SCCP access point and remote resources. The long message rule enables XUDT towards both STPs, so TCAP messages that do not fit in a UDT can still be sent.

```
m3ua as create AS1 IPSP mode SE ipspType CLIENT traffic-mode loadshare
m3ua as create AS2 IPSP mode SE ipspType CLIENT traffic-mode loadshare
m3ua asp create ASP1 Assoc1
m3ua asp create ASP2 Assoc2
m3ua as add AS1 ASP1
m3ua as add AS2 ASP2
m3ua asp start ASP1
m3ua asp start ASP2
m3ua route add AS1 1001 -1 -1
m3ua route add AS2 1002 -1 -1

sccp sap create 1 1 2000 3
sccp dest create 1 1 1001 1002 0 255 255
sccp rsp create 1 1001 0 0
sccp rsp create 2 1002 0 0
sccp csp create 1 1001
sccp csp create 2 1002
sccp lmr create 1 1001 1002 xudt
```

### Global Title Translation

Rule 1 accepts traffic from the network for the gateway's own GT on SSN 8 (SMSC) and delivers it to the local SCCP user. Rule 2 forwards locally originated traffic on GT to the STPs, loadshared on SLS bit 0 with failover to the remaining one. Restricting each rule to one direction keeps the gateway from acting as an SCCP relay: a message from the network for any GT other than its own finds no rule and is rejected instead of being sent back to the STP.

The pattern of rule 1 (address indicator `18`, SSN present) matches SSN 8 only, so messages for the own GT with any other SSN, or without an SSN, are rejected. Rule 2 uses address indicator `16` without an SSN, so outgoing traffic for any SSN, such as HLR (6) and MSC (8), is forwarded.

Translation targets must carry a point code, which selects the MTP3 destination, hence address indicator `17` (route on GT, PC present). With the default `removeSpc true` the PC is stripped before sending, so the STP sees a pure GT address.

```
sccp primary_add create 1 17 2000 0 0 1 4 15550001000
sccp rule create 1 K 18 0 8 0 1 4 15550001000 Solitary 1 RemoteOriginated

sccp primary_add create 2 17 1001 0 0 1 4 -
sccp backup_add create 3 17 1002 0 0 1 4 -
sccp rule create 2 K 16 0 0 0 1 4 * Loadshared 2 3 Bit0 LocalOriginated
```
