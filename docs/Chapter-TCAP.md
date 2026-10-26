# TCAP

The Transaction Capabilities Application Part (TCAP, ITU-T Q.771 to Q.775) lets applications at two signaling points run many concurrent dialogs over one SCCP subsystem, each dialog carrying operations (components) encoded in ASN.1. MAP, CAP and INAP are all built on TCAP. This document covers the jSS7 API; the ASN.1 layer lives in the `asn` module.

## Contents

- [Objects](#objects)
- [Dialog lifecycle](#dialog-lifecycle)
- [Components](#components)
- [Timers and limits](#timers-and-limits)
- [Client example](#client-example)

## Objects

| Type | Role |
|------|------|
| `TCAPStackImpl` | The stack. Created with an `SccpProvider` and the local subsystem number; `start()` registers it as the SCCP listener for that subsystem. |
| `TCAPProvider` | Obtained from `TCAPStack.getProvider()`. Creates dialogs and exposes the two factories. |
| `TCListener` | Implemented by the application and registered with `TCAPProvider.addTCListener`. Receives every incoming dialog primitive and the timeout callbacks. |
| `Dialog` | One transaction with a peer. Components are queued with `sendComponent` and flushed by `send(TC-BEGIN / CONTINUE / END / U-ABORT / UNI)`. |
| `DialogPrimitiveFactory` | Creates the dialog requests (`createBegin`, `createContinue`, `createEnd`, `createUAbort`, `createUni`), application context names and user information. |
| `ComponentPrimitiveFactory` | Creates components: `Invoke`, `ReturnResult`, `ReturnResultLast`, `ReturnError`, `Reject`, plus `OperationCode`, `ErrorCode` and `Parameter`. |

## Dialog lifecycle

1. **Open**: `provider.getNewDialog(localAddress, remoteAddress)` returns a structured dialog with a fresh local transaction id. `getNewUnstructuredDialog` returns a dialog for a single TC-UNI message. The protocol class can be given as a third argument; class 1 keeps message order per SLS.
2. **Queue components**: `dialog.sendComponent(component)`; several components can be queued before one send.
3. **Send**: `dialog.send(TCBeginRequest)` for the first message, `send(TCContinueRequest)` for the following ones, `send(TCEndRequest)` to close, or `send(TCUserAbortRequest)` to abort. The first message should carry an `ApplicationContextName`.
4. **Receive**: the peer's messages arrive as `onTCBegin`, `onTCContinue`, `onTCEnd`, `onTCUserAbort`, `onTCPAbort` (provider abort, for example unknown transaction id) or `onTCUni`. Each indication gives access to the dialog and the received components.
5. **Release**: a dialog ends with TC-END or an abort from either side, after which `onDialogReleased` is called. A dialog that stays idle longer than the idle timeout gets `onDialogTimeout`; the application may call `dialog.keepAlive()` to extend it or let the stack abort it.

Every incoming `Invoke` should be answered in one of three ways; an invoke that is never answered stays registered on the dialog until the dialog is released, and on the sending side its invoke timer fires `onInvokeTimeout`:

- a result: `ReturnResultLast`, optionally preceded by `ReturnResult` segments,
- an error: `ReturnError` or `Reject`,
- or `dialog.processInvokeWithoutAnswer(invokeId)` when the operation has no result by design.

## Components

`Invoke` carries an invoke id (`dialog.getNewInvokeId()`), an `OperationCode` (local integer or global OID), an optional `Parameter` tree and an invoke class (`InvokeClass.Class1` to `Class4`, which decides whether a result, an error, both or neither is expected). A linked invoke references the id of the invoke it responds to.

Parameters are built with `Parameter` objects or by encoding an ASN.1 structure from the `asn` module into the parameter's data. Unanswered invokes are reported to the application through `onInvokeTimeout`.

## Timers and limits

| Setting | Meaning | Default |
|---------|---------|---------|
| `setDialogIdleTimeout` | Idle time after which `onDialogTimeout` fires | 60 s |
| `setInvokeTimeout` | Time an invoke may stay unanswered | 30 s |
| `setMaxDialogs` | Maximum concurrent dialogs; further `getNewDialog` calls fail | 5000 |
| `setDialogIdRangeStart` / `End` | Local transaction id range, useful when several stacks share a peer | 1 to 1048575 (`0xFFFFF`) |
| `setPreviewMode` | Decode traffic passively without sending anything, for monitoring | `false` |
| `setDoNotSendProtocolVersion` | Omit the protocol version from the dialogue portion, for peers that reject it | `false` |

Defaults are read from the stack after `start()`; check `TCAPStackImpl` if you rely on an exact value.

## Client example

Opens a dialog to subsystem 8 at point code 2, sends one invoke with operation code 12 in a TC-BEGIN, and ends the dialog when the peer continues.

```java
package example;

import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.SccpProvider;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.TCAPStackImpl;
import org.mobicents.protocols.ss7.tcap.api.ComponentPrimitiveFactory;
import org.mobicents.protocols.ss7.tcap.api.TCAPException;
import org.mobicents.protocols.ss7.tcap.api.TCAPProvider;
import org.mobicents.protocols.ss7.tcap.api.TCAPSendException;
import org.mobicents.protocols.ss7.tcap.api.TCListener;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.*;
import org.mobicents.protocols.ss7.tcap.asn.ApplicationContextName;
import org.mobicents.protocols.ss7.tcap.asn.comp.Invoke;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCode;

public class TcapClient implements TCListener {

    private static final long[] ACN = { 0, 4, 0, 0, 1, 0, 19, 2 };   // example application context
    private final TCAPProvider tcapProvider;

    public TcapClient(SccpProvider sccpProvider) throws Exception {
        TCAPStackImpl stack = new TCAPStackImpl(sccpProvider, 8);      // local SSN 8
        stack.start();
        this.tcapProvider = stack.getProvider();
        this.tcapProvider.addTCListener(this);
    }

    public void sendInvoke() throws TCAPException, TCAPSendException {
        SccpAddress local = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 1, null, 8);
        SccpAddress remote = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 2, null, 8);
        Dialog dialog = tcapProvider.getNewDialog(local, remote);

        ComponentPrimitiveFactory cpf = tcapProvider.getComponentPrimitiveFactory();
        Invoke invoke = cpf.createTCInvokeRequest();
        invoke.setInvokeId(dialog.getNewInvokeId());
        OperationCode oc = cpf.createOperationCode();
        oc.setLocalOperationCode(12L);
        invoke.setOperationCode(oc);
        dialog.sendComponent(invoke);                                  // no parameter in this example

        ApplicationContextName acn = tcapProvider.getDialogPrimitiveFactory().createApplicationContextName(ACN);
        TCBeginRequest begin = tcapProvider.getDialogPrimitiveFactory().createBegin(dialog);
        begin.setApplicationContextName(acn);
        dialog.send(begin);
    }

    @Override
    public void onTCContinue(TCContinueIndication ind) {
        TCEndRequest end = tcapProvider.getDialogPrimitiveFactory().createEnd(ind.getDialog());
        end.setTermination(TerminationType.Basic);
        try {
            ind.getDialog().send(end);
        } catch (TCAPSendException e) {
            throw new RuntimeException(e);
        }
    }

    @Override public void onDialogTimeout(Dialog d) { d.keepAlive(); }
    @Override public void onTCBegin(TCBeginIndication ind) { }
    @Override public void onTCEnd(TCEndIndication ind) { }
    @Override public void onTCUni(TCUniIndication ind) { }
    @Override public void onTCUserAbort(TCUserAbortIndication ind) { }
    @Override public void onTCPAbort(TCPAbortIndication ind) { }
    @Override public void onTCNotice(TCNoticeIndication ind) { }
    @Override public void onDialogReleased(Dialog d) { }
    @Override public void onInvokeTimeout(Invoke invoke) { }
}
```

The server side implements the same `TCListener`, reacts in `onTCBegin` by reading the components from the indication, queues `ReturnResultLast` components on `ind.getDialog()` and answers with `createContinue` or `createEnd`.
