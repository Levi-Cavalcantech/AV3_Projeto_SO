
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SimuladorSistemaArquivos {

    private static final String DISK = "sistema.jfs";
    private static final String JOURNAL = "journal.bin";

    // Estrutura de arquivo ou diretório virtual
    static class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        String name;
        boolean directory;
        String content = "";
        Node parent;
        Map<String, Node> children = new LinkedHashMap<>();

        Node(String name, boolean directory, Node parent) {
            this.name = name;
            this.directory = directory;
            this.parent = parent;
        }
    }

    private Node root;
    private Node current;

    public static void main(String[] args) {
        new SimuladorSistemaArquivos().start();
    }

    private void start() {
        try {
            root = new Node("", true, null);
            current = root;

            recover();

            Scanner scanner = new Scanner(System.in);

            System.out.println("Simulador de Sistema de Arquivos");
            System.out.println("Digite help para ver os comandos.");

            while (true) {
                System.out.print(getPath(current) + " $ ");

                String line = scanner.nextLine().trim();

                if (line.isEmpty()) continue;

                String[] p = line.split("\\s+");

                try {
                    if (p[0].equalsIgnoreCase("exit")) {
                        break;
                    }

                    execute(p);

                } catch (Exception e) {
                    System.out.println("Erro: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.out.println("Erro ao iniciar: " + e.getMessage());
        }
    }

    private void execute(String[] p) throws Exception {
        switch (p[0].toLowerCase()) {
            case "mkdir":
                require(p, 2);
                mkdir(p[1]);
                break;

            case "touch":
                require(p, 2);
                touch(p[1]);
                break;

            case "write":
                require(p, 3);
                write(p[1], p[2]);
                break;

            case "cat":
                require(p, 2);
                cat(p[1]);
                break;

            case "cp":
                require(p, 3);
                copy(p[1], p[2]);
                break;

            case "rm":
                require(p, 2);
                removeFile(p[1]);
                break;

            case "rmdir":
                require(p, 2);
                removeDirectory(p[1]);
                break;

            case "mv":
                require(p, 3);
                move(p[1], p[2]);
                break;

            case "ls":
                list(p.length > 1 ? p[1] : ".");
                break;

            case "cd":
                require(p, 2);
                cd(p[1]);
                break;

            case "pwd":
                System.out.println(getPath(current));
                break;

            case "tree":
                tree(root, "");
                break;

            case "help":
                help();
                break;

            default:
                System.out.println("Comando desconhecido.");
        }
    }

    // Cria diretório
    private void mkdir(String path) throws Exception {
        Node parent = parentOf(path);
        String name = nameOf(path);

        validateName(name);
        checkAvailable(parent, name);

        parent.children.put(name, new Node(name, true, parent));

        commit();
        System.out.println("Diretório criado.");
    }

    // Cria arquivo vazio
    private void touch(String path) throws Exception {
        Node parent = parentOf(path);
        String name = nameOf(path);

        validateName(name);
        checkAvailable(parent, name);

        parent.children.put(name, new Node(name, false, parent));

        commit();
        System.out.println("Arquivo criado.");
    }

    // Grava conteúdo em um arquivo
    private void write(String path, String content) throws Exception {
        Node file = resolve(path);

        if (file.directory) {
            throw new Exception("Não é um arquivo.");
        }

        file.content = content;

        commit();
        System.out.println("Conteúdo gravado.");
    }

    // Lê conteúdo de um arquivo
    private void cat(String path) throws Exception {
        Node file = resolve(path);

        if (file.directory) {
            throw new Exception("Não é um arquivo.");
        }

        System.out.println(file.content);
    }

    // Copia arquivo
    private void copy(String source, String destination)
            throws Exception {

        Node file = resolve(source);

        if (file.directory) {
            throw new Exception("Só é possível copiar arquivos.");
        }

        Node parent = parentOf(destination);
        String name = nameOf(destination);

        validateName(name);
        checkAvailable(parent, name);

        Node copy = new Node(name, false, parent);
        copy.content = file.content;

        parent.children.put(name, copy);

        commit();
        System.out.println("Arquivo copiado.");
    }

    // Apaga arquivo
    private void removeFile(String path) throws Exception {
        Node file = resolve(path);

        if (file.directory) {
            throw new Exception("Use rmdir para apagar diretórios.");
        }

        file.parent.children.remove(file.name);

        commit();
        System.out.println("Arquivo apagado.");
    }

    // Apaga diretório, inclusive seu conteúdo
    private void removeDirectory(String path) throws Exception {
        Node dir = resolve(path);

        if (!dir.directory) {
            throw new Exception("Não é um diretório.");
        }

        if (dir == root) {
            throw new Exception("Não pode apagar a raiz.");
        }

        if (isInside(current, dir)) {
            throw new Exception(
                "Não pode apagar o diretório atual ou um de seus pais."
            );
        }

        dir.parent.children.remove(dir.name);

        commit();
        System.out.println("Diretório apagado.");
    }

    // Renomeia ou move arquivo/diretório
    private void move(String source, String destination)
            throws Exception {

        Node node = resolve(source);
        Node parent = parentOf(destination);
        String name = nameOf(destination);

        validateName(name);

        if (node == root) {
            throw new Exception("Não pode renomear a raiz.");
        }

        if (node.directory && isInside(parent, node)) {
            throw new Exception(
                "Não pode mover um diretório para dentro dele mesmo."
            );
        }

        checkAvailable(parent, name);

        node.parent.children.remove(node.name);

        node.name = name;
        node.parent = parent;

        parent.children.put(name, node);

        commit();
        System.out.println("Movido/renomeado.");
    }

    // Lista diretório
    private void list(String path) throws Exception {
        Node dir = resolve(path);

        if (!dir.directory) {
            throw new Exception("Não é um diretório.");
        }

        for (Node node : dir.children.values()) {
            System.out.println(
                (node.directory ? "[DIR] " : "[FILE] ") + node.name
            );
        }
    }

    private void cd(String path) throws Exception {
        Node node = resolve(path);

        if (!node.directory) {
            throw new Exception("Não é um diretório.");
        }

        current = node;
    }

    // Resolve caminhos absolutos e relativos
    private Node resolve(String path) throws Exception {
        Node node = path.startsWith("/") ? root : current;

        if (path.equals("/") || path.equals(".")) {
            return node;
        }

        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;

            if (part.equals("..")) {
                if (node.parent != null) node = node.parent;
                continue;
            }

            if (!node.directory) {
                throw new Exception("Componente não é diretório.");
            }

            Node next = node.children.get(part);

            if (next == null) {
                throw new Exception("Caminho não encontrado: " + path);
            }

            node = next;
        }

        return node;
    }

    private Node parentOf(String path) throws Exception {
        int index = path.lastIndexOf('/');

        if (index == -1) {
            return current;
        }

        String parentPath = path.substring(0, index);

        if (parentPath.isEmpty()) parentPath = "/";

        Node parent = resolve(parentPath);

        if (!parent.directory) {
            throw new Exception("O destino não é um diretório.");
        }

        return parent;
    }

    private String nameOf(String path) {
        int index = path.lastIndexOf('/');
        return index == -1 ? path : path.substring(index + 1);
    }

    private void validateName(String name) throws Exception {
        if (name.isEmpty() || name.equals(".") || name.equals("..")
                || name.contains("\\") || name.contains("|")) {
            throw new Exception("Nome inválido.");
        }
    }

    private void checkAvailable(Node parent, String name)
            throws Exception {

        if (parent.children.containsKey(name)) {
            throw new Exception("Já existe um item com esse nome.");
        }
    }

    private boolean isInside(Node node, Node ancestor) {
        while (node != null) {
            if (node == ancestor) return true;
            node = node.parent;
        }
        return false;
    }

    private String getPath(Node node) {
        if (node == root) return "/";

        List<String> parts = new ArrayList<>();

        while (node != null && node != root) {
            parts.add(node.name);
            node = node.parent;
        }

        Collections.reverse(parts);

        return "/" + String.join("/", parts);
    }

    private void tree(Node node, String indent) {
        for (Node child : node.children.values()) {
            System.out.println(
                indent + (child.directory ? "[DIR] " : "[FILE] ")
                + child.name
            );

            if (child.directory) {
                tree(child, indent + "  ");
            }
        }
    }

    // Serializa o sistema inteiro em memória
    private byte[] serialize() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(root);
        }

        return buffer.toByteArray();
    }

    // Grava o estado binário no disco e no journal
    private void commit() throws IOException {
        byte[] data = serialize();

        // Atualiza o arquivo que representa o sistema
        Files.write(Paths.get(DISK), data);

        // Acrescenta um registro binário ao journal
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(
                    new FileOutputStream(JOURNAL, true)))) {

            out.writeInt(data.length);
            out.write(data);
        }
    }

    // Recupera o último estado válido do journal
    private void recover() throws Exception {
        Path journal = Paths.get(JOURNAL);

        if (!Files.exists(journal)) {
            Files.createFile(journal);
            commit();
            return;
        }

        byte[] lastState = null;

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(
                    new FileInputStream(JOURNAL)))) {

            while (true) {
                try {
                    int length = in.readInt();

                    if (length <= 0 || length > 100_000_000) {
                        break;
                    }

                    byte[] data = new byte[length];
                    in.readFully(data);

                    // Guarda o último registro completo
                    lastState = data;

                } catch (EOFException e) {
                    break;
                }
            }
        }

        if (lastState != null) {
            try (ObjectInputStream in = new ObjectInputStream(
                    new ByteArrayInputStream(lastState))) {

                root = (Node) in.readObject();
                current = root;

                // Reconstrói o arquivo principal
                Files.write(Paths.get(DISK), lastState);

                System.out.println("Sistema recuperado do journal.");
                return;
            }
        }

        // Caso o journal esteja vazio ou corrompido
        root = new Node("", true, null);
        current = root;
        commit();
    }

    private void require(String[] p, int n) {
        if (p.length < n) {
            throw new IllegalArgumentException("Argumentos insuficientes.");
        }
    }

    private void help() {
        System.out.println("Comandos:");
        System.out.println("mkdir <dir>");
        System.out.println("touch <arquivo>");
        System.out.println("write <arquivo> <conteudo>");
        System.out.println("cat <arquivo>");
        System.out.println("cp <origem> <destino>");
        System.out.println("rm <arquivo>");
        System.out.println("rmdir <diretorio>");
        System.out.println("mv <origem> <destino>");
        System.out.println("ls [diretorio]");
        System.out.println("cd <diretorio>");
        System.out.println("pwd");
        System.out.println("tree");
        System.out.println("exit");
    }
}
