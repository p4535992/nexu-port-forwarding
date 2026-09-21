package it.nexu.forwarding;

public final class Launcher {
    private Launcher() { }
    public static void main(String[] args) {
        if(java.util.Arrays.asList(args).contains("--version")) { System.out.println("Nexu Port Forwarding 1.1.0"); return; }
        if(java.util.Arrays.asList(args).contains("--smoke-test")) System.setProperty("nexu.smokeTest","true");
        javafx.application.Application.launch(NexuApplication.class,args);
    }
}
