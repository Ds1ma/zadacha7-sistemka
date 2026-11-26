import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
public class JavaObfuscator {
    private enum TokenType {
        STRING, CHAR, COMMENT, WHITESPACE, WORD, NUMBER, SYMBOL
    }
    private static class Token {
        final TokenType type;
        String value;
        Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }
    }
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "true", "false", "null", "var", "record", "sealed", "permits"
    );
    private static final Set<String> NON_TYPE_KEYWORDS = Set.of(
            "return", "throw", "new", "case", "package", "import", "extends", "implements",
            "throws", "instanceof", "else", "do", "try", "finally", "assert", "if", "while", "for", "switch", "catch"
    );
    public static void main(String[] args) {
        String dirPath;
        if (args.length > 0) {
            dirPath = args[0];
        } else {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Введите путь к директории с .java файлами: ");
            dirPath = scanner.nextLine().trim();
        }
        Path directory = Paths.get(dirPath);
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            System.out.println("Ошибка: указанная директория не существует.");
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            if (javaFiles.isEmpty()) {
                System.out.println("В директории не найдено файлов с расширением .java.");
                return;
            }
            for (Path file : javaFiles) {
                obfuscateFile(file);
            }
        } catch (IOException e) {
            System.out.println("Ошибка при чтении директории: " + e.getMessage());
        }
    }
    private static void obfuscateFile(Path filePath) throws IOException {
        String originalCode = Files.readString(filePath, StandardCharsets.UTF_8);
        List<Token> tokens = tokenize(originalCode);
        List<Token> codeTokens = tokens.stream()
                .filter(t -> t.type != TokenType.COMMENT && t.type != TokenType.WHITESPACE)
                .collect(Collectors.toList());
        String oldClassName = findClassName(codeTokens);
        LinkedHashSet<String> identifiers = collectUserIdentifiers(codeTokens, oldClassName);
        Set<String> existingWords = codeTokens.stream()
                .filter(t -> t.type == TokenType.WORD)
                .map(t -> t.value)
                .collect(Collectors.toSet());
        int nameLength = identifiers.size() <= 52 ? 1 : 2;
        List<String> shortNames = generateShortNames(identifiers.size(), nameLength, existingWords);
        Map<String, String> renameMap = new LinkedHashMap<>();
        int index = 0;
        for (String id : identifiers) {
            renameMap.put(id, shortNames.get(index++));
        }
        boolean inPackageOrImport = false;
        for (Token token : codeTokens) {
            if (token.type == TokenType.WORD && (token.value.equals("package") || token.value.equals("import"))) {
                inPackageOrImport = true;
            } else if (token.type == TokenType.SYMBOL && token.value.equals(";")) {
                inPackageOrImport = false;
            }
            if (!inPackageOrImport && token.type == TokenType.WORD && renameMap.containsKey(token.value)) {
                token.value = renameMap.get(token.value);
            }
        }
        String compressedCode = buildCompressedCode(codeTokens);
        Path targetPath = filePath;
        if (oldClassName != null && renameMap.containsKey(oldClassName)) {
            String newClassName = renameMap.get(oldClassName);
            targetPath = filePath.getParent().resolve(newClassName + ".java");
        }
        Files.writeString(targetPath, compressedCode, StandardCharsets.UTF_8);
        if (!targetPath.equals(filePath)) {
            Files.deleteIfExists(filePath);
        }
        System.out.printf("Файл обработан: %s -> %s%n", filePath.getFileName(), targetPath.getFileName());
        System.out.printf("Найдено идентификаторов: %d (длина новых имён: %d символ(а))%n",
                identifiers.size(), nameLength);
        System.out.printf("Размер: %d байт -> %d байт%n%n",
                originalCode.getBytes(StandardCharsets.UTF_8).length,
                compressedCode.getBytes(StandardCharsets.UTF_8).length);
    }
    private static List<Token> tokenize(String src) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < n && Character.isWhitespace(src.charAt(i))) i++;
                tokens.add(new Token(TokenType.WHITESPACE, src.substring(start, i)));
            }
            else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                int start = i;
                i += 2;
                while (i < n && src.charAt(i) != '\n') i++;
                tokens.add(new Token(TokenType.COMMENT, src.substring(start, i)));
            }
            else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i = Math.min(n, i + 2);
                tokens.add(new Token(TokenType.COMMENT, src.substring(start, i)));
            }
            else if (c == '"') {
                int start = i;
                if (i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                    i += 3;
                    while (i + 2 < n && !(src.charAt(i) == '"' && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"')) {
                        if (src.charAt(i) == '\\') i++;
                        i++;
                    }
                    i = Math.min(n, i + 3);
                } else {
                    i++;
                    while (i < n && src.charAt(i) != '"') {
                        if (src.charAt(i) == '\\') i++;
                        i++;
                    }
                    i = Math.min(n, i + 1);
                }
                tokens.add(new Token(TokenType.STRING, src.substring(start, i)));
            }
            else if (c == '\'') {
                int start = i;
                i++;
                while (i < n && src.charAt(i) != '\'') {
                    if (src.charAt(i) == '\\') i++;
                    i++;
                }
                i = Math.min(n, i + 1);
                tokens.add(new Token(TokenType.CHAR, src.substring(start, i)));
            }
            else if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < n && Character.isJavaIdentifierPart(src.charAt(i))) i++;
                tokens.add(new Token(TokenType.WORD, src.substring(start, i)));
            }
            else if (Character.isDigit(c)) {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_' || src.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, src.substring(start, i)));
            }
            else {
                tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c)));
                i++;
            }
        }
        return tokens;
    }
    private static String findClassName(List<Token> tokens) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            Token curr = tokens.get(i);
            Token prev = i > 0 ? tokens.get(i - 1) : null;
            Token next = tokens.get(i + 1);
            boolean isTypeKeyword = curr.type == TokenType.WORD &&
                    (curr.value.equals("class") || curr.value.equals("interface")
                            || curr.value.equals("enum") || curr.value.equals("record"));
            boolean notClassLiteral = prev == null || !prev.value.equals(".");
            if (isTypeKeyword && notClassLiteral && next.type == TokenType.WORD) {
                return next.value;
            }
        }
        return null;
    }
    private static LinkedHashSet<String> collectUserIdentifiers(List<Token> tokens, String className) {
        LinkedHashSet<String> identifiers = new LinkedHashSet<>();
        if (className != null) {
            identifiers.add(className);
        }
        boolean inPackageOrImport = false;
        boolean inVarDeclaration = false;
        boolean inParamList = false;
        int parenDepth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Token curr = tokens.get(i);
            Token prev = i > 0 ? tokens.get(i - 1) : null;
            Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            if (curr.type == TokenType.WORD && (curr.value.equals("package") || curr.value.equals("import"))) {
                inPackageOrImport = true;
            } else if (curr.type == TokenType.SYMBOL) {
                switch (curr.value) {
                    case ";" -> {
                        inPackageOrImport = false;
                        inVarDeclaration = false;
                    }
                    case "{" -> {
                        inVarDeclaration = false;
                        inParamList = false;
                    }
                    case "(" -> parenDepth++;
                    case ")" -> {
                        parenDepth = Math.max(0, parenDepth - 1);
                        if (parenDepth == 0) inParamList = false;
                    }
                }
            }
            if (inPackageOrImport || curr.type != TokenType.WORD) continue;
            if (KEYWORDS.contains(curr.value) || curr.value.equals("main")) continue;
            if (prev != null && (prev.value.equals(".") || prev.value.equals("@"))) continue;
            boolean precededByType = prev != null && (
                    (prev.type == TokenType.WORD && !NON_TYPE_KEYWORDS.contains(prev.value))
                            || prev.value.equals("]")
                            || prev.value.equals(">")
                            || prev.value.equals(".") // для varargs '...'
            );
            if (precededByType && next != null && next.value.equals("(")) {
                identifiers.add(curr.value);
                inParamList = true;
            }
            else if (next != null && Set.of("=", ";", ",", ")", ":").contains(next.value)) {
                if (precededByType || (inVarDeclaration && prev != null && prev.value.equals(",") && parenDepth == 0)) {
                    identifiers.add(curr.value);
                    if (parenDepth == 0) {
                        inVarDeclaration = true;
                    }
                }
            }
         else if (inParamList && precededByType) {
                identifiers.add(curr.value);
            }
        }
        return identifiers;
    }
    private static List<String> generateShortNames(int count, int length, Set<String> forbidden) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        List<String> result = new ArrayList<>(count);
        if (length == 1) {
            for (int i = 0; i < alphabet.length() && result.size() < count; i++) {
                String candidate = String.valueOf(alphabet.charAt(i));
                if (!forbidden.contains(candidate) && !KEYWORDS.contains(candidate)) {
                    result.add(candidate);
                }
            }
        }
        if (result.size() < count) {
            for (int i = 0; i < alphabet.length() && result.size() < count; i++) {
                for (int j = 0; j < alphabet.length() && result.size() < count; j++) {
                    String candidate = "" + alphabet.charAt(i) + alphabet.charAt(j);
                    if (!forbidden.contains(candidate) && !KEYWORDS.contains(candidate)) {
                        result.add(candidate);
                    }
                }
            }
        }
        return result;
    }
    private static String buildCompressedCode(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            Token curr = tokens.get(i);
            sb.append(curr.value);
            if (i + 1 < tokens.size()) {
                Token next = tokens.get(i + 1);
                if (needsSpaceBetween(curr, next)) {
                    sb.append(' ');
                }
            }
        }
        return sb.toString();
    }
    private static boolean needsSpaceBetween(Token left, Token right) {
        boolean leftIsWordOrNum = left.type == TokenType.WORD || left.type == TokenType.NUMBER;
        boolean rightIsWordOrNum = right.type == TokenType.WORD || right.type == TokenType.NUMBER;
        if (leftIsWordOrNum && rightIsWordOrNum) {
            return true;
        }
        // Предотвращаем склеивание операторов вроде + + в ++ или - - в --
        if (left.type == TokenType.SYMBOL && right.type == TokenType.SYMBOL) {
            String pair = left.value + right.value;
            return pair.equals("++") || pair.equals("--");
        }
        return false;
    }
}
