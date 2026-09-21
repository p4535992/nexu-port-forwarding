package it.nexu.forwarding.importer;

import it.nexu.forwarding.config.SafeFiles;
import java.io.*;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.events.AliasEvent;

/** YAML syntax only: compose nodes, then accept plain scalar/list/map data. Never construct Java objects. */
public final class TabbyYamlReader {
    public static final int MAX_BYTES=4_000_000;
    private TabbyYamlReader() { }
    public static TabbyImport.Report read(Path file,String installation) throws IOException {
        byte[] bytes=SafeFiles.readBytes(file,MAX_BYTES);
        try { return parse(bytes,installation); } finally { Arrays.fill(bytes,(byte)0); }
    }
    public static TabbyImport.Report parse(byte[] bytes,String installation) throws IOException {
        if(bytes.length>MAX_BYTES) throw new IOException("Configurazione Tabby oltre 4 MB.");
        Object plain;
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            LoaderOptions options=new LoaderOptions();
            options.setAllowDuplicateKeys(false); options.setAllowRecursiveKeys(false);
            options.setMaxAliasesForCollections(0); options.setNestingDepthLimit(40); options.setCodePointLimit(MAX_BYTES);
            options.setTagInspector(tag -> false);
            Yaml yaml=new Yaml(new SafeConstructor(options));
            // Collection alias limits alone do not reject scalar aliases (including aliases of map keys).
            for (var event : yaml.parse(new StringReader(text)))
                if (event instanceof AliasEvent) throw new IOException("Alias YAML non ammesso.");
            Node node=yaml.compose(new StringReader(text)); // Single document only, no load()/Java object deserialization.
            plain=new PlainTree().convert(node,0);
        } catch(Exception e) {
            // Parser exceptions may echo passwords/private keys from the input line: never expose their text/cause.
            throw new IOException("YAML Tabby rifiutato: usare un singolo documento UTF-8, senza alias, tag personalizzati o chiavi duplicate; profondità massima 40.");
        }
        return TabbyImport.convert(plain,installation);
    }
    private static final class PlainTree {
        private final Set<Node> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        private int count;
        Object convert(Node node,int depth) throws IOException {
            if(node==null) return null;
            if(depth>40 || ++count>100_000 || !seen.add(node)) throw new IOException("Struttura YAML non ammessa.");
            if(node instanceof MappingNode mapping && Tag.MAP.equals(node.getTag())) {
                Map<String,Object> result=new LinkedHashMap<>();
                for(NodeTuple tuple:mapping.getValue()) {
                    if(!(tuple.getKeyNode() instanceof ScalarNode key) || !Tag.STR.equals(key.getTag())) throw new IOException("Chiave non testuale.");
                    String name=key.getValue();
                    if(name.length()>1024 || result.containsKey(name)) throw new IOException("Chiave duplicata o troppo lunga.");
                    result.put(name,convert(tuple.getValueNode(),depth+1));
                }
                return result;
            }
            if(node instanceof SequenceNode sequence && Tag.SEQ.equals(node.getTag())) {
                List<Object> result=new ArrayList<>();
                for(Node item:sequence.getValue()) result.add(convert(item,depth+1));
                return result;
            }
            if(node instanceof ScalarNode scalar) {
                Tag tag=scalar.getTag();
                if(Tag.NULL.equals(tag)) return null;
                if(Tag.BOOL.equals(tag)) {
                    return switch(scalar.getValue().toLowerCase(Locale.ROOT)) {
                        case "true","yes","on" -> true;
                        case "false","no","off" -> false;
                        default -> throw new IOException("Booleano non valido.");
                    };
                }
                if(Tag.STR.equals(tag)||Tag.INT.equals(tag)||Tag.FLOAT.equals(tag)||Tag.TIMESTAMP.equals(tag)) return scalar.getValue();
            }
            throw new IOException("Tag YAML non ammesso.");
        }
    }
    /** Suggestions only: no file is read until the user chooses Import. */
    public static List<Path> suggestedPaths() {
        List<Path> result=new ArrayList<>();
        add(result,System.getenv("TABBY_CONFIG_DIRECTORY"),"");
        if(System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows")) {
            add(result,System.getenv("APPDATA"),"tabby");
            add(result,Path.of(System.getProperty("user.home"),"AppData","Roaming").toString(),"tabby");
        } else {
            add(result,System.getenv("XDG_CONFIG_HOME"),"tabby");
            add(result,Path.of(System.getProperty("user.home"),".config").toString(),"tabby");
        }
        return result.stream().distinct().toList();
    }
    private static void add(List<Path> paths,String base,String folder) {
        if(base==null||base.isBlank()) return;
        try { Path p=Path.of(base); if(p.isAbsolute()) paths.add(p.resolve(folder).resolve("config.yaml")); }
        catch(InvalidPathException ignored) { }
    }
}
