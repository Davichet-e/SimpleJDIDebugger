## Java JDI

This is just a simple project for the System Software Lecture on JKU university.
It is a simple Debugger for Java programs.

```
Commands:
help -> print all commands
set {n} -> set a breakpoint on the `n`th line
step {type} -> step into if type = into, over if type = over
run -> runs the program until the next breakpoint
list -> list all breakpoints
delete {n} -> delete (if present) the breakpoint located on the `n`th line
print -> print all the variables on the scope
stacktrace -> prints the stacktrace
```

# Run

To run it, just execute:

```
$ cd src\main\java
$ javac me\davichete\jdi\*.java
$ java -cp . me\davichete\jdi\Debugger.java
```
