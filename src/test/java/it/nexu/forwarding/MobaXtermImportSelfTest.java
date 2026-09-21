package it.nexu.forwarding;

import it.nexu.forwarding.importer.MobaXtermImport;
import it.nexu.forwarding.model.TunnelProfile;
import java.util.*;

public final class MobaXtermImportSelfTest {
    private static int passed;
    private MobaXtermImportSelfTest() { }
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        passed=0;
        String ini="""
            [Misc]
            PasswordsInRegistry=0
            [PortForwarding]
            0000.siteA=Local;alice@ssh.example.com:2222;db.internal:5432;15432;0;No SSH key selected;127.0.0.1;No proxy selected;0
            0001.siteA=Remote;alice@ssh.example.com:2222;127.0.0.1:8080;18080;0;No SSH key selected;127.0.0.1;No proxy selected;0
            0002.siteA=Dynamic;alice@ssh.example.com:2222;;1080;0;No SSH key selected;127.0.0.1;No proxy selected;0
            [Passwords]
            alice@ssh.example.com=SHOULD_NOT_BE_PARSED
            """;
        var report=MobaXtermImport.parse(ini,"");
        eq(report.entries(),3,"three forwarding entries detected"); eq(report.candidates().size(),3,"three forwarding entries converted");
        var local=report.candidates().get(0).profile(); eq(local.mode(),TunnelProfile.Mode.LOCAL,"local mode");eq(local.bindPort(),15432,"local listen port");eq(local.targetHost(),"db.internal","local target");eq(local.targetPort(),5432,"local target port");
        eq(local.sshHost(),"ssh.example.com","SSH hostname imported");eq(local.sshPort(),2222,"SSH port imported");eq(local.username(),"alice","SSH user imported");eq(local.origin(),TunnelProfile.Origin.MOBAXTERM,"origin stored");eq(local.installation(),"siteA","installation inferred from key");
        var remote=report.candidates().get(1).profile();eq(remote.mode(),TunnelProfile.Mode.REMOTE,"remote mode");eq(remote.bindPort(),18080,"remote listen port");eq(remote.targetHost(),"127.0.0.1","remote target");
        var dyn=report.candidates().get(2).profile();eq(dyn.mode(),TunnelProfile.Mode.DYNAMIC,"dynamic mode");eq(dyn.bindPort(),1080,"dynamic listen port");eq(dyn.targetPort(),0,"dynamic has no fixed target");
        check(report.warnings().stream().noneMatch(x->x.contains("SHOULD_NOT_BE_PARSED")),"password section is ignored and never surfaced");
        var selected=report.candidates().stream().map(MobaXtermImport.Candidate::profile).toList();var plan=MobaXtermImport.append(List.of(),selected);eq(plan.added().size(),3,"append imports candidates");var again=MobaXtermImport.append(plan.profiles(),selected);eq(again.duplicates(),3,"reimport skips duplicates");
        String proxy="[PortForwarding]\nx=Local;u@h:22;t:80;8080;0;No SSH key selected;127.0.0.1;proxy.example:8080;0\n";var rejected=MobaXtermImport.parse(proxy,"x");eq(rejected.candidates().size(),0,"proxy-configured tunnel is not silently changed");eq(rejected.skipped(),1,"proxy-configured tunnel is reported skipped");
        String bind="[PortForwarding]\nx=Local;u@h:22;t:80;8080;0;No SSH key selected;0.0.0.0;No proxy selected;0\n";check(MobaXtermImport.parse(bind,"x").candidates().get(0).requiresReview(),"non-loopback bind requires review");
        System.out.println("MOBAXTERM IMPORT RESULT: "+passed+" passed, 0 failed");
    }
    private static void eq(Object a,Object b,String n){check(Objects.equals(a,b),n);} private static void check(boolean ok,String n){if(!ok)throw new AssertionError(n);passed++;System.out.println("PASS "+n);}
}
