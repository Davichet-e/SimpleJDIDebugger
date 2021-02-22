package me.davichete.jdi;

public class Debuggee {

    public static void main(String[] args) {
        String[] jezu = { "hello", "bro" };
        String jpda = "Java Platform Debugger Architecture";
        System.out.println("Hi Everyone, Welcome to " + jpda); // add a break point here

        String jdi = "Java Debug Interface"; // add a break point here and also stepping in here
        String text = "Today, we'll dive into " + jdi;
        method();
        System.out.println(text);
    }

    private static void method() {
        int i = 0;
        foo();
        int j = i + 2;
        System.out.println(j);
    }

    private static int foo() {
        int j = 7;
        return j;
    }

}
