package it.nexu.forwarding;

public final class Launcher {
    private Launcher() { }
    public static void main(String[] args) {
        if(java.util.Arrays.asList(args).contains("--version")) { System.out.println("NexU Port Forwarding 0.2.1"); return; }
        if(java.util.Arrays.asList(args).contains("--smoke-test")) System.setProperty("nexu.smokeTest","true");
        javafx.application.Application.launch(NexuApplication.class,args);
    }
}
