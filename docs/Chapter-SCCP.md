# SCCP

The Signaling Connection Control Part (SCCP, ITU-T Q.711 to Q.716) sits on top of MTP3 and adds addressing beyond point codes: subsystem numbers, global titles and the translation between them. jSS7 implements the connectionless service (protocol classes 0 and 1) with UDT, XUDT and LUDT messages, and SCCP management (SCMG) for subsystem and point code status.

## Contents

- [Routing and Global Title Translation](#routing-and-global-title-translation)
  - [Matching a rule](#matching-a-rule)
  - [Translating the address](#translating-the-address)
  - [Choosing the destination](#choosing-the-destination)
  - [Examples](#examples)
- [Using the stack](#using-the-stack)
  - [SCCP user example](#sccp-user-example)

## Routing and Global Title Translation

A message whose called party address is routed on global title (routing indicator 0) is matched against the configured rules. The winning rule names a primary address, and optionally a backup and further addresses, that supply the point code, subsystem number and new digits. The result replaces the called party address and the message is forwarded on MTP3.

Rules and addresses are configured with `sccp rule`, `sccp primary_add` and `sccp backup_add`, see the [Shell Command Reference](Chapter-Shell.md#rule-management), section *Rule Management*. Messages routed on DPC and SSN (routing indicator 1) bypass translation entirely.

### Matching a rule

The called party digits are compared with the rule's `digits` pattern. The pattern may be split into sections with `/`, and each section may use:

| Token | Meaning |
|-------|---------|
| `1234` | Exactly these digits |
| `?` | Any single digit |
| `*` | Any number of digits, including none. Only meaningful as the last section. |

`800/????/9` matches an eight-digit number that starts with `800` and ends with `9`. `800800/*` matches anything starting with `800800`. A pattern without wildcards must match the whole number.

Rules are kept sorted, and the first match wins. Rules with an origination type (`LocalOriginated` or `RemoteOriginated`) come before rules that apply to all messages; within the same origination type, exact patterns come before patterns with `?`, and those before patterns with `*`. The address indicator of the pattern (routing indicator, GTI, presence of PC and SSN) must also match the called party address.

### Translating the address

The rule's `mask` has one section per pattern section and decides where each section of the translated digits comes from:

| Mask | Digits of this section |
|------|------------------------|
| `K` | Kept from the dialed number |
| `R` | Replaced by the same section of the selected address. If that section is `-`, nothing is emitted. |

The rest of the translated address always comes from the selected primary or backup address:

- **Routing indicator and point code** are taken from the address. The address must carry a point code.
- **Subsystem number** is taken from the address if it is non-zero, otherwise from the dialed address.
- **Global title**: if the translated digits are empty, the result has no global title. Otherwise the global title type (GTI, translation type, numbering plan, nature of address) is taken from the address; if the address has no global title, the type is taken from the dialed address and only the digits change.

### Choosing the destination

The rule type decides which address is used when more than one is configured. Each candidate is checked for availability first: the remote point code must be known and allowed, and for DPC+SSN routing the remote subsystem must be known and allowed.

| Rule type | Behaviour |
|-----------|-----------|
| `Solitary` | Primary only. If it is unavailable the message is returned with a cause matching the reason: *Subsystem failure*, *MTP failure* or *SCCP failure*. *No translation for this address* is returned only when the address itself is undefined. |
| `Dominant` | Primary if available, otherwise backup. |
| `Loadshared` | One bit of the SLS (`Bit0` to `Bit4`) selects primary or backup for class 1 messages; class 0 messages, which carry no SLS, alternate between the two. If the selected one is down the other is used. |
| `Sgls` | Primary, backup and any number of extra backup addresses form a list; every available entry stays in the list and entry `SLS mod count` is used, so traffic is spread evenly over the reachable destinations. |

### Examples

The tables show the called party address before and after translation. Address indicator values: `16` = GTI 4, route on GT; `17` = GTI 4 + PC, route on GT; `19` = GTI 4 + PC + SSN, route on GT; `67` = PC + SSN, route on DPC and SSN, no GT; `1` = PC only; `3` = PC + SSN, no GT.

**Example 1: replace the global title with point code and subsystem.**

| | AI | PC | SSN | TT | NP | NAI | Digits |
|---|---|---|---|---|---|---|---|
| Dialed | 16 | | | 0 | 1 | 4 | 123456789 |
| Rule pattern, mask `R` | 16 | | | 0 | 1 | 4 | 123456789 |
| Primary address | 67 | 123 | 8 | | | | `-` |
| **Translated** | **67** | **123** | **8** | | | | |

```
sccp primary_add create 1 67 123 8 0 0 0 -
sccp rule create 1 R 16 0 0 0 1 4 123456789 Solitary 1
```

**Example 2: partial match, rewrite the prefix and suffix, keep the middle.** The primary address has no global title, so the dialed GT type is kept with the new digits.

| | AI | PC | SSN | TT | NP | NAI | Digits |
|---|---|---|---|---|---|---|---|
| Dialed | 16 | | | 0 | 1 | 4 | 80012349 |
| Rule pattern, mask `R/K/R` | 16 | | | 0 | 1 | 4 | `800/????/9` |
| Primary address | 1 | 123 | | | | | `123/-/4` |
| **Translated** | **17** | **123** | | 0 | 1 | 4 | **12312344** |

```
sccp primary_add create 2 1 123 0 0 0 0 123/-/4
sccp rule create 2 R/K/R 16 0 0 0 1 4 800/????/9 Solitary 2
```

**Example 3: strip a prefix, add point code and subsystem.**

| | AI | PC | SSN | TT | NP | NAI | Digits |
|---|---|---|---|---|---|---|---|
| Dialed | 16 | | | 0 | 1 | 4 | 80080012345 |
| Rule pattern, mask `R/K` | 16 | | | 0 | 1 | 4 | `800800/*` |
| Primary address | 3 | 123 | 8 | | | | `-/-` |
| **Translated** | **19** | **123** | **8** | 0 | 1 | 4 | **12345** |

```
sccp primary_add create 3 3 123 8 0 0 0 -/-
sccp rule create 3 R/K 16 0 0 0 1 4 800800/* Solitary 3
```

**Example 4: catch-all, keep the digits, add point code and subsystem.**

| | AI | PC | SSN | TT | NP | NAI | Digits |
|---|---|---|---|---|---|---|---|
| Dialed | 16 | | | 0 | 1 | 4 | 4414257897897 |
| Rule pattern, mask `K` | 16 | | | 0 | 1 | 4 | `*` |
| Primary address | 3 | 123 | 8 | | | | `-` |
| **Translated** | **19** | **123** | **8** | 0 | 1 | 4 | **4414257897897** |

```
sccp primary_add create 4 3 123 8 0 0 0 -
sccp rule create 4 K 16 0 0 0 1 4 * Solitary 4
```

> [!TIP]
> Because rules are sorted by specificity, the catch-all of example 4 can coexist with the more specific rules above it; it is only consulted when nothing else matches.

## Using the stack

`SccpStackImpl` is the entry point. It needs at least one `Mtp3UserPart` (in this fork an `M3UAManagementImpl`) registered with `setMtp3UserPart(id, ...)`, where `id` is the value later used as `<mtp3-id>` in `sccp sap create`.

`SccpStack.getSccpProvider()` returns the `SccpProvider` an SCCP user works with:

- `registerSccpListener(ssn, listener)` binds a `SccpListener` to a local subsystem number. Incoming messages whose called subsystem matches are delivered to `onMessage`; SCMG events arrive through `onNotice`, `onState`, `onPcState` and the coordination callbacks.
- `getMessageFactory()` creates `SccpDataMessage` instances for protocol class 0 (`createDataMessageClass0`) or class 1 (`createDataMessageClass1`, with an explicit SLS for in-sequence delivery).
- `getParameterFactory()` creates hop counters, importance and other parameters.
- `send(message)` hands a message to the router.

Message type (UDT, XUDT or LUDT) is chosen by the stack from the long message rules (`sccp lmr`) and the data length; segmentation and reassembly are transparent to the user.

### SCCP user example

A listener on subsystem 8 that echoes an answer to every received message:

```java
package example;

import java.io.IOException;

import org.mobicents.protocols.ss7.indicator.NatureOfAddress;
import org.mobicents.protocols.ss7.indicator.NumberingPlan;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.RemoteSccpStatus;
import org.mobicents.protocols.ss7.sccp.SccpListener;
import org.mobicents.protocols.ss7.sccp.SccpProvider;
import org.mobicents.protocols.ss7.sccp.SignallingPointStatus;
import org.mobicents.protocols.ss7.sccp.impl.SccpStackImpl;
import org.mobicents.protocols.ss7.sccp.message.SccpDataMessage;
import org.mobicents.protocols.ss7.sccp.message.SccpNoticeMessage;
import org.mobicents.protocols.ss7.sccp.parameter.GlobalTitle;
import org.mobicents.protocols.ss7.sccp.parameter.HopCounter;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.mtp.Mtp3UserPart;

public class EchoUser implements SccpListener {

    private static final int LOCAL_SSN = 8;
    private final SccpProvider sccpProvider;

    public EchoUser(Mtp3UserPart mtp3) {
        SccpStackImpl stack = new SccpStackImpl("sccp");
        stack.setMtp3UserPart(1, mtp3);      // <mtp3-id> 1 in "sccp sap create"
        stack.start();
        this.sccpProvider = stack.getSccpProvider();
        this.sccpProvider.registerSccpListener(LOCAL_SSN, this);
    }

    public void stop() {
        this.sccpProvider.deregisterSccpListener(LOCAL_SSN);
    }

    @Override
    public void onMessage(SccpDataMessage message) {
        SccpAddress remote = message.getCallingPartyAddress();
        SccpAddress local = message.getCalledPartyAddress();
        byte[] request = message.getData();
        byte[] answer = request;                              // application logic goes here

        HopCounter hc = sccpProvider.getParameterFactory().createHopCounter(5);
        SccpDataMessage reply = sccpProvider.getMessageFactory().createDataMessageClass1(
                remote, local, answer, message.getSls(), LOCAL_SSN, false, hc, null);
        try {
            sccpProvider.send(reply);
        } catch (IOException e) {
            // transport failure
        }
    }

    @Override public void onNotice(SccpNoticeMessage message) { }          // returned / undeliverable messages
    @Override public void onCoordRequest(int dpc, int ssn, int multiplicityIndicator) { }
    @Override public void onCoordResponse(int dpc, int ssn, int multiplicityIndicator) { }
    @Override public void onState(int dpc, int ssn, boolean inService, int multiplicityIndicator) { }
    @Override public void onPcState(int dpc, SignallingPointStatus status, int restrictedImportanceLevel,
                                    RemoteSccpStatus remoteSccpStatus) { }
}
```

A global-title address for the calling party can be built with `GlobalTitle.getInstance(tt, NumberingPlan, NatureOfAddress, digits)` and `new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0, gt, ssn)`.
