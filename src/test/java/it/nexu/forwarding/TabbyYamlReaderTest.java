package it.nexu.forwarding;

import it.nexu.forwarding.importer.*;
import it.nexu.forwarding.model.TunnelProfile;
import it.nexu.forwarding.config.ProfileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs with real SnakeYAML in Maven, never with a stub or a replacement parser. */
final class TabbyYamlReaderTest {
    @TempDir Path directory;
    private static TabbyImport.Report parse(String text) throws IOException {
        return TabbyYamlReader.parse(text.getBytes(StandardCharsets.UTF_8),"");
    }
    private static String fixture() throws IOException {
        try(var in=TabbyYamlReaderTest.class.getResourceAsStream("/tabby/config.example.yaml")) {
            if(in==null) throw new IOException("Missing fixture");
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    @Test void actualYamlSyntaxMapsAllForwardingTypes() throws Exception {
        var result=parse(fixture()); assertEquals(3,result.candidates().size());
        assertEquals(List.of(TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.REMOTE,TunnelProfile.Mode.DYNAMIC),
            result.candidates().stream().map(c->c.profile().mode()).toList());
        assertEquals("Example installation",result.candidates().getFirst().profile().installation());
        assertEquals(2222,result.candidates().getFirst().profile().sshPort());
    }
    @Test void tabbyV8NestedForwardedPortsFixtureImportsFiveProfilesWithoutCredentials() throws Exception {
        try(var in=TabbyYamlReaderTest.class.getResourceAsStream("/tabby/config-v8-forwarded-ports.example.yaml")) {
            assertNotNull(in);
            var result=TabbyYamlReader.parse(in.readAllBytes(),"");
            assertEquals(3,result.sshProfiles());
            assertEquals(5,result.candidates().size());
            assertEquals(0,result.skippedProfiles());
            assertEquals(0,result.skippedForwards());
            assertEquals(List.of("demo-user-a","demo-user-a","demo-user-b","demo-user-c","demo-user-c"),
                result.candidates().stream().map(c->c.profile().username()).toList());
            assertEquals(List.of(TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.REMOTE,TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.REMOTE),
                result.candidates().stream().map(c->c.profile().mode()).toList());
            assertEquals(10022,result.candidates().getFirst().profile().sshPort());
            assertEquals("localhost",result.candidates().getFirst().profile().bindHost());
            assertEquals(18080,result.candidates().getFirst().profile().bindPort());
            assertEquals("web-service.example.invalid",result.candidates().getFirst().profile().targetHost());
            assertTrue(result.candidates().stream().noneMatch(TabbyImport.Candidate::requiresReview));
        }
    }

    @Test void byteOrderMarkAndCrLfAreSupported() throws Exception {
        assertEquals(3,parse("\ufeff"+fixture().replace("\n","\r\n")).candidates().size());
    }
    @Test void inlineMappingsAndQuotedPortsAreSupported() throws Exception {
        var result=parse("profiles: [{type: ssh, id: demo, name: Demo, options: {host: ssh.example.com, user: demo, auth: password, forwardedPorts: [{type: Dynamic, host: '::1', port: '1080'}]}}]\n");
        assertEquals(1,result.candidates().size()); assertEquals("::1",result.candidates().getFirst().profile().bindHost());
    }
    @Test void duplicateMappingKeysAreRejected() {
        assertThrows(IOException.class,()->parse("profiles: []\nprofiles: []\n"));
    }
    @Test void customJavaTagsAreRejectedWithoutInstantiation() {
        assertThrows(IOException.class,()->parse("profiles: !!java.util.ArrayList []\n"));
    }
    @Test void recursiveAliasesAreRejected() {
        assertThrows(IOException.class,()->parse("profiles: &profiles [*profiles]\n"));
    }
    @Test void collectionAliasesAndMergeKeysAreRejected() {
        assertThrows(IOException.class,()->parse("base: &defaults {user: demo}\nprofiles: [{options: {<<: *defaults}}]\n"));
    }
    @Test void scalarAliasesAreRejectedToo() {
        assertThrows(IOException.class,()->parse("secret: &a plain\nsecret2: *a\nprofiles: []\n"));
    }
    @Test void aliasesOfMappingKeysAreRejected() {
        assertThrows(IOException.class,()->parse("&key source: value\nother: *key\nprofiles: []\n"));
    }
    @Test void multipleYamlDocumentsAreRejected() {
        assertThrows(IOException.class,()->parse("profiles: []\n---\nprofiles: []\n"));
    }
    @Test void errorsDoNotEchoCredentialSnippetsOrCauses() {
        IOException error=assertThrows(IOException.class,()->parse("profiles: [\n password: synthetic-should-not-appear\n"));
        assertFalse(error.toString().contains("synthetic-should-not-appear")); assertNull(error.getCause());
    }
    @Test void malformedUtf8IsRejected() {
        assertThrows(IOException.class,()->TabbyYamlReader.parse(new byte[]{(byte)0xc3,(byte)0x28},""));
    }
    @Test void oversizedDocumentIsRejected() {
        assertThrows(IOException.class,()->TabbyYamlReader.parse(new byte[TabbyYamlReader.MAX_BYTES+1],""));
    }
    @Test void deeplyNestedInputIsRejected() {
        assertThrows(IOException.class,()->parse("profiles: "+"[".repeat(70)+"0"+"]".repeat(70)));
    }
    @Test void yamlInputIsNeverRewrittenOrCopiedIntoProfiles() throws Exception {
        String yaml=fixture().replace("auth: password","auth: password\n      password: synthetic-only-secret");
        Path source=directory.resolve("config.yaml"); Files.writeString(source,yaml);
        byte[] before=Files.readAllBytes(source);
        var result=TabbyYamlReader.read(source,"Office");
        assertArrayEquals(before,Files.readAllBytes(source));
        String encoded=new String(ProfileStore.encode(result.candidates().stream().map(TabbyImport.Candidate::profile).toList()),StandardCharsets.UTF_8);
        assertFalse(encoded.contains("synthetic-only-secret"));
        assertFalse(encoded.contains("config.yaml"));
        try(var files=Files.list(directory)) { assertEquals(1,files.count()); }
    }
}
