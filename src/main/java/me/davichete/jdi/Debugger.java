package me.davichete.jdi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.*;
import com.sun.jdi.StackFrame;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.StepRequest;
import com.sun.jdi.request.EventRequestManager;

public class Debugger {

    private static String COMMANDS = "Commands:\nhelp -> print all commands\nset {n} -> set a breakpoint on the `n`th line\nstep {type} -> step into if type = into, over if type = over\nrun -> runs the program until the next breakpoint\nlist -> list all breakpoints\ndelete {n} -> delete (if present) the breakpoint located on the `n`th line\nprint -> print all the variables on the scope\nstacktrace -> prints the stacktrace\n";

    ReferenceType rt;

    private Class<Debuggee> debugClass;
    private List<Integer> breakPointLines;

    public Class<Debuggee> getDebugClass() {
        return debugClass;
    }

    public void setDebugClass(Class<Debuggee> debugClass) {
        this.debugClass = debugClass;
    }

    public List<Integer> getBreakPointLines() {
        return breakPointLines;
    }

    public void setBreakPointLines(List<Integer> breakPointLines) {
        this.breakPointLines = breakPointLines;
    }

    /**
     * Sets the debug class as the main argument in the connector and launches the
     * VM
     * 
     * @return VirtualMachine
     * @throws IOException
     * @throws IllegalConnectorArgumentsException
     * @throws VMStartException
     */
    public VirtualMachine connectAndLaunchVM()
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        VirtualMachine vm = launchingConnector.launch(arguments);
        Process proc = vm.process();
        new Redirection(proc.getErrorStream(), System.err).start();
        new Redirection(proc.getInputStream(), System.out).start();
        return vm;
    }

    /**
     * Creates a request to prepare the debug class, add filter as the debug class
     * and enables it
     * 
     * @param vm
     */
    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }

    /**
     * Sets the break points at the line numbers mentioned in breakPointLines array
     * 
     * @param vm
     * @param event
     * @throws AbsentInformationException
     */
    public void setBreakPoints(VirtualMachine vm) throws AbsentInformationException {
        for (int lineNumber : breakPointLines) {
            if (!setBreakPoint(lineNumber, vm))
                System.out.println("Invalid breakpoint");
        }
    }

    public boolean setBreakPoint(int line, VirtualMachine vm) throws AbsentInformationException {
        var locations = rt.locationsOfLine(line);
        if (locations.isEmpty()) {
            return false;
        }
        Location location = locations.get(0);
        BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
        bpReq.enable();
        return true;
    }

    /**
     * Displays the visible variables
     * 
     * @param event
     * @throws IncompatibleThreadStateException
     * @throws AbsentInformationException
     */
    public void displayVariables(LocatableEvent event)
            throws IncompatibleThreadStateException, AbsentInformationException {
        StackFrame stackFrame = event.thread().frame(0);
        if (stackFrame.location().toString().contains(debugClass.getName())) {
            Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
            System.out.println("Variables at " + stackFrame.location().toString() + " > ");
            for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                if (entry.getValue() instanceof ArrayReference) {
                    System.out.println(entry.getKey().name() + " = " + ((ArrayReference) entry.getValue()).getValues());
                } else {
                    System.out.println(entry.getKey().name() + " = " + entry.getValue());
                }
            }
        }
    }

    /**
     * Enables step request for a break point
     * 
     * @param vm
     * @param event
     */
    public void enableStepRequest(VirtualMachine vm, int sr) {
        // enable step request for last break point

        StepRequest stepRequest = vm.eventRequestManager().createStepRequest(vm.allThreads().get(0),
                StepRequest.STEP_LINE, sr);

        stepRequest.addClassExclusionFilter("java.*");
        stepRequest.addClassExclusionFilter("jdk.*");
        stepRequest.addClassExclusionFilter("sun.*");
        stepRequest.enable();
    }

    public void deleteBreakpoint(EventRequestManager evm, int line) {
        Optional<BreakpointRequest> er = evm.breakpointRequests().stream()
                .filter((br) -> br.location().lineNumber() == line).findFirst();
        if (er.isPresent())
            evm.deleteEventRequest(er.get());
        else
            System.out.println("Breakpoint not found");
    }

    public void foo(Event event, Scanner sc, VirtualMachine vm) throws Exception {
        System.out.print("Choose\n> ");
        System.out.flush();

        String s;
        while (!(s = sc.nextLine()).equals("run")) {
            if (s.startsWith("set ")) {
                int line = Integer.parseInt(s.split(" ")[1]);
                if (!this.setBreakPoint(line, vm))
                    System.out.println("Invalid line to set a breakpoint");

            } else if (s.equals("step over")) {
                this.enableStepRequest(vm, StepRequest.STEP_OVER);
                break;

            } else if (s.equals("step into")) {
                this.enableStepRequest(vm, StepRequest.STEP_INTO);
                break;

            } else if (s.startsWith("delete")) {
                int line = Integer.parseInt(s.split(" ")[1]);
                this.deleteBreakpoint(vm.eventRequestManager(), line);

            } else if (s.equals("list")) {
                System.out.println(vm.eventRequestManager().breakpointRequests());

            } else if (s.equals("print")) {
                this.displayVariables((LocatableEvent) event);

            } else if (s.equals("stacktrace")) {
                System.out.println(((LocatableEvent) event).thread().frames().stream()
                        .map(f -> String.format("%s:%s", f.location().method().name(), f.location()))
                        .collect(Collectors.toList()));

            } else {
                System.out.println(COMMANDS);
            }

            System.out.print("\nChoose\n> ");
            System.out.flush();
        }
    }

    public static void main(String[] args) throws Exception {

        System.out.println("Welcome to DDebuggerR (David Debugger Rocks)\n\n" + COMMANDS);

        Debugger debuggerInstance = new Debugger();
        debuggerInstance.setDebugClass(Debuggee.class);
        VirtualMachine vm = null;

        Scanner sc = new Scanner(System.in);
        try {
            vm = debuggerInstance.connectAndLaunchVM();

            debuggerInstance.enableClassPrepareRequest(vm);

            EventSet eventSet = null;
            while ((eventSet = vm.eventQueue().remove()) != null) {
                for (Event event : eventSet) {
                    if (event instanceof ClassPrepareEvent) {

                        debuggerInstance.rt = ((ClassPrepareEvent) event).referenceType();
                        debuggerInstance.setBreakPoint(
                                debuggerInstance.rt.methodsByName("main").get(0).location().lineNumber(), vm);
                    }

                    else if (event instanceof BreakpointEvent) {
                        event.request().disable();
                        debuggerInstance.foo(event, sc, vm);
                    }

                    if (event instanceof StepEvent) {
                        ((StepEvent) event).request().disable();
                        debuggerInstance.foo(event, sc, vm);
                    }
                    vm.resume();
                }
            }
        } catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sc.close();
        }

    }

}

class Redirection extends Thread {
    Reader in;
    Writer out;

    Redirection(InputStream is, OutputStream os) {
        super();
        in = new InputStreamReader(is);
        out = new OutputStreamWriter(os);
    }

    public void run() {
        char[] buf = new char[1024];
        int n;
        try {
            while ((n = in.read(buf, 0, 1024)) >= 0)
                out.write(buf, 0, n);
            out.flush();
        } catch (IOException e) {
        }
    }
}
