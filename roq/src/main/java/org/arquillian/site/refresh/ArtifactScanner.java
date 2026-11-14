package org.arquillian.site.refresh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.jgit.lib.Repository;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Reads recursive Maven models and explicitly supported Gradle literals from Git objects without executing builds. */
public final class ArtifactScanner {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]+)['\"]");
    /** Creates a scanner with no filesystem or network effects. */
    public ArtifactScanner() {}

    /** Returns caller-owned module rows rooted at repository.relative_path and bound to the resolved commit. */
    public ArrayNode scan(Repository git, String revision, ObjectNode repository) throws IOException {
        String commit = RepositoryCollector.commit(git, revision).name();
        String root = RepositoryCollector.path(repository.path("relative_path").asText());
        ArrayNode rows = JsonNodeFactory.instance.arrayNode();
        traverse(git, commit, root, repository, rows, new HashMap<>(), new HashSet<>());
        return rows;
    }

    /** Walks declared Maven/profile or Gradle settings modules, rejecting escaping paths and cycles. */
    private void traverse(Repository git, String revision, String directory, ObjectNode repository, ArrayNode rows,
            Map<String, ObjectNode> models, Set<String> visited) throws IOException {
        if (!visited.add(directory)) throw new IOException("Duplicate or cyclic module path: " + directory);
        if (visited.size() > 2000 || directory.split("/").length > 100) throw new IOException("Repository metadata module limit exceeded");
        String prefix = directory.isEmpty() ? "" : directory + "/";
        ObjectNode model;
        if (RepositoryCollector.read(git, revision, prefix + "pom.xml") != null) {
            model = effective(pom(git, revision, prefix + "pom.xml", models, new HashSet<>()));
        } else {
            model = gradle(git, revision, prefix);
            if (model == null) {
                if (visited.size() > 1) throw new IOException("Declared module has no supported build metadata: " + directory);
                return;
            }
        }
        ObjectNode row = RepositoryCollector.row("git:module:", repository.path("id").asText(), directory.isEmpty() ? "." : directory);
        row.setAll(model.deepCopy());
        row.put("repository", repository.path("id").asText()).put("relative_path", prefix).put("revision", revision)
                .put("source_url", repository.path("html_url").asText() + "/blob/" + revision + "/" + model.path("source_path").asText());
        rows.add(row);
        for (JsonNode module : model.path("modules")) {
            String child = module.asText();
            if (child.startsWith("/") || child.contains("\\") || child.matches("^[A-Za-z]:.*")) throw new IOException("Unsafe module path: " + child);
            if (PROPERTY.matcher(child).find()) {
                ((ArrayNode) row.path("diagnostics")).add("Unresolved module path: " + child);
                continue;
            }
            String resolved = RepositoryCollector.path(prefix + child);
            traverse(git, revision, resolved, repository, rows, models, visited);
        }
    }

    /** Caches raw inherited Maven declarations so each child can interpolate its own complete property context. */
    private ObjectNode pom(Repository git, String revision, String path, Map<String, ObjectNode> cache, Set<String> active) throws IOException {
        if (cache.containsKey(path)) return cache.get(path);
        if (!active.add(path) || active.size() > 100) throw new IOException("Cyclic or excessive Maven parent chain: " + path);
        String xml = RepositoryCollector.read(git, revision, path);
        if (xml == null) throw new IOException("Missing declared Maven POM: " + path);
        Element project = xml(xml);
        ObjectNode model = JsonNodeFactory.instance.objectNode().put("build_system", "maven").put("source_path", path);
        ArrayNode diagnostics = model.putArray("diagnostics");
        model.putObject("source_payload").put("path", path).put("revision", revision).put("text", xml);
        ObjectNode inherited = JsonNodeFactory.instance.objectNode();
        Element parent = child(project, "parent");
        if (parent != null) {
            Element relative = child(parent, "relativePath");
            if (relative == null || !relative.getTextContent().isBlank()) {
                if (relative == null && !path.contains("/")) diagnostics.add("Unresolved external Maven parent: " + text(parent, "artifactId"));
                else {
                    String parentPath = RepositoryCollector.path(path.substring(0, path.lastIndexOf('/') + 1) + (relative == null ? "../pom.xml" : relative.getTextContent().strip()));
                    if (!parentPath.endsWith(".xml")) parentPath = RepositoryCollector.path(parentPath + "/pom.xml");
                    if (RepositoryCollector.read(git, revision, parentPath) != null) inherited = pom(git, revision, parentPath, cache, active);
                    else diagnostics.add("Unresolved external Maven parent: " + text(parent, "artifactId"));
                }
            } else diagnostics.add("External Maven parent has local resolution disabled");
            for (String field : List.of("groupId", "artifactId", "version")) {
                String expected = text(parent, field);
                if (!inherited.isEmpty() && expected != null && !PROPERTY.matcher(expected).find() && !expected.equals(effective(inherited).path(field).asText())) {
                    diagnostics.add("Local parent coordinates differ from declared parent; external parent remains unresolved");
                    inherited = JsonNodeFactory.instance.objectNode();
                }
            }
        }
        Map<String, String> properties = new LinkedHashMap<>();
        inherited.path("properties").properties().forEach(entry -> properties.put(entry.getKey(), entry.getValue().asText()));
        Element declared = child(project, "properties");
        if (declared != null) for (Element property : children(declared, null)) properties.put(property.getTagName(), property.getTextContent().strip());
        for (String field : List.of("groupId", "version", "artifactId")) {
            String value = text(project, field);
            if (value == null && !field.equals("artifactId")) value = inherited.path(field).isTextual() ? inherited.path(field).asText() : text(parent, field);
            if (value != null) {
                properties.put("project." + field, value);
                properties.put("pom." + field, value);
            }
            if (parent != null && text(parent, field) != null) properties.put("project.parent." + field, text(parent, field));
        }
        properties.forEach(model.putObject("properties")::put);
        if (parent != null) model.set("parent", fields(parent, "groupId", "artifactId", "version", "relativePath"));
        for (String field : List.of("groupId", "artifactId", "version", "name", "description", "packaging", "url")) {
            String value = text(project, field);
            if (value == null && List.of("groupId", "version", "description", "url").contains(field)) value = inherited.path(field).isTextual() ? inherited.path(field).asText() : text(parent, field);
            if (value != null) model.put(field, value);
        }
        if (!model.has("packaging")) model.put("packaging", "jar");
        ArrayNode licenses = model.putArray("licenses");
        Element declaredLicenses = child(project, "licenses");
        if (declaredLicenses == null) inherited.path("licenses").forEach(licenses::add);
        else for (Element license : children(declaredLicenses, "license")) licenses.add(fields(license, "name", "url", "distribution", "comments"));
        ArrayNode management = model.putArray("dependency_management");
        inherited.path("dependency_management").forEach(management::add);
        for (Element dependency : children(child(child(project, "dependencyManagement"), "dependencies"), "dependency")) {
            ObjectNode entry = fields(dependency, "groupId", "artifactId", "version", "type", "scope", "classifier", "optional");
            management.add(entry);
            if (entry.path("scope").asText().equals("import")) diagnostics.add("External BOM import retained without resolution: " + dependencyKey(entry));
        }
        ArrayNode dependencies = model.putArray("dependencies");
        inherited.path("dependencies").forEach(dependencies::add);
        for (Element dependency : children(child(project, "dependencies"), "dependency")) dependencies.add(fields(dependency, "groupId", "artifactId", "version", "type", "scope", "classifier", "optional"));
        Set<String> modules = new java.util.LinkedHashSet<>();
        for (Element module : children(child(project, "modules"), "module")) modules.add(module.getTextContent().strip());
        for (Element profile : children(child(project, "profiles"), "profile")) {
            for (Element module : children(child(profile, "modules"), "module")) modules.add(module.getTextContent().strip());
            if (child(profile, "properties") != null || child(profile, "dependencies") != null) diagnostics.add("Profile-conditional metadata retained in source: " + text(profile, "id"));
        }
        modules.forEach(model.putArray("modules")::add);
        active.remove(path);
        cache.put(path, model);
        return model;
    }

    /** Interpolates a copy after inheritance, then applies child dependency overrides and managed versions by resolved coordinates. */
    private ObjectNode effective(ObjectNode raw) {
        Map<String, String> properties = new LinkedHashMap<>();
        raw.path("properties").properties().forEach(entry -> properties.put(entry.getKey(), entry.getValue().asText()));
        ObjectNode row = raw.deepCopy();
        ArrayNode diagnostics = (ArrayNode) row.path("diagnostics");
        for (String field : List.of("groupId", "artifactId", "version", "name", "description", "packaging", "url", "parent", "properties", "licenses", "dependency_management", "dependencies", "modules")) if (row.has(field)) row.set(field, interpolate(row.get(field), properties, diagnostics));
        Map<String, JsonNode> management = new LinkedHashMap<>();
        row.path("dependency_management").forEach(value -> management.put(dependencyKey(value), value));
        Map<String, JsonNode> dependencies = new LinkedHashMap<>();
        row.path("dependencies").forEach(value -> dependencies.put(dependencyKey(value), value));
        ArrayNode resolved = row.putArray("dependencies");
        for (JsonNode dependency : dependencies.values()) {
            JsonNode managed = management.get(dependencyKey(dependency));
            if (!dependency.has("version") && managed != null && managed.has("version")) ((ObjectNode) dependency).set("version", managed.get("version"));
            if (!dependency.has("version")) diagnostics.add("Unresolved dependency version: " + dependencyKey(dependency));
            resolved.add(dependency);
        }
        return row;
    }

    /** Resolves only model values; original source text and revision provenance remain untouched. */
    private JsonNode interpolate(JsonNode value, Map<String, String> properties, ArrayNode diagnostics) {
        if (value.isTextual()) return JsonNodeFactory.instance.textNode(resolve(value.asText(), properties, diagnostics));
        if (value instanceof ObjectNode object) for (Map.Entry<String, JsonNode> entry : object.properties()) object.set(entry.getKey(), interpolate(entry.getValue(), properties, diagnostics));
        if (value instanceof ArrayNode array) for (int index = 0; index < array.size(); index++) array.set(index, interpolate(array.get(index), properties, diagnostics));
        return value;
    }

    /** Parses a Maven document with DTDs, external entities, XInclude and external schemas disabled. */
    private Element xml(String source) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element project = factory.newDocumentBuilder().parse(new InputSource(new StringReader(source))).getDocumentElement();
            if (!project.getTagName().equals("project")) throw new IOException("Maven XML root must be project");
            return project;
        } catch (ParserConfigurationException | SAXException failure) {
            throw new IOException("Invalid or unsafe Maven XML", failure);
        }
    }

    /** Reads only scalar assignments, quoted settings includes and string dependency coordinates from Gradle scripts. */
    private ObjectNode gradle(Repository git, String revision, String prefix) throws IOException {
        String path = prefix + "build.gradle";
        String build = RepositoryCollector.read(git, revision, path);
        if (build == null) { path += ".kts"; build = RepositoryCollector.read(git, revision, path); }
        if (build == null) return null;
        ObjectNode row = JsonNodeFactory.instance.objectNode().put("build_system", "gradle").put("source_path", path);
        row.putObject("source_payload").put("path", path).put("revision", revision).put("text", build);
        ArrayNode diagnostics = row.putArray("diagnostics");
        ArrayNode modules = row.putArray("modules");
        ArrayNode dependencies = row.putArray("dependencies");
        row.putArray("licenses");
        row.putObject("properties");
        String settings = RepositoryCollector.read(git, revision, prefix + "settings.gradle");
        if (settings == null) settings = RepositoryCollector.read(git, revision, prefix + "settings.gradle.kts");
        if (settings != null) ((ObjectNode) row.path("source_payload")).put("settings", settings);
        for (String line : (build + "\n" + (settings == null ? "" : settings)).lines().toList()) {
            String statement = line.strip();
            Matcher scalar = Pattern.compile("^(group|version|description|rootProject\\.name)\\s*=\\s*(.+?);?$").matcher(statement);
            if (scalar.matches()) {
                String key = switch (scalar.group(1)) { case "group" -> "groupId"; case "rootProject.name" -> "name"; default -> scalar.group(1); };
                String value = scalar.group(2);
                Matcher literal = QUOTED.matcher(value);
                if (literal.matches() && !value.contains("$") && !value.contains("#{")) row.put(key, literal.group(1));
                else { row.put(key, value); diagnostics.add("Unresolved Gradle expression: " + statement); }
            } else if (statement.matches("^include(?:\\s|\\().*")) {
                if (!QUOTED.matcher(statement.substring(7)).replaceAll("").matches("[\\s,();]*") || statement.contains("$")) {
                    diagnostics.add("Unresolved Gradle include: " + statement);
                    continue;
                }
                Matcher included = QUOTED.matcher(statement);
                boolean found = false;
                while (included.find()) { modules.add(included.group(1).replaceFirst("^:", "").replace(':', '/')); found = true; }
                if (!found) diagnostics.add("Unresolved Gradle include: " + statement);
            } else if (statement.matches("^(api|implementation|compile|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly)(?:\\s|\\().*")) {
                String scope = statement.split("[\\s(]", 2)[0];
                String arguments = statement.substring(scope.length()).strip().replaceFirst(";$", "").strip();
                if (arguments.startsWith("(") && arguments.endsWith(")")) arguments = arguments.substring(1, arguments.length() - 1).strip();
                Matcher coordinate = Pattern.compile("(['\"])([^'\"]+)\\1").matcher(arguments);
                if (coordinate.matches() && coordinate.group(2).split(":", -1).length == 3 && !coordinate.group(2).contains("$")) {
                    String[] parts = coordinate.group(2).split(":", -1);
                    dependencies.addObject().put("groupId", parts[0]).put("artifactId", parts[1]).put("version", parts[2]).put("scope", scope);
                } else diagnostics.add("Unresolved Gradle dependency: " + statement);
            } else if (!statement.isBlank() && !statement.equals("}") && !statement.startsWith("//")) diagnostics.add("Uninterpreted Gradle statement: " + statement);
        }
        return row;
    }

    /** Resolves known property chains while preserving unresolved/cyclic expressions and recording diagnostics. */
    private String resolve(String value, Map<String, String> properties, ArrayNode diagnostics) {
        String original = value;
        Set<String> seen = new HashSet<>();
        for (int depth = 0; depth < 100 && seen.add(value); depth++) {
            Matcher matcher = PROPERTY.matcher(value);
            StringBuilder replaced = new StringBuilder();
            while (matcher.find()) matcher.appendReplacement(replaced, Matcher.quoteReplacement(properties.getOrDefault(matcher.group(1), matcher.group())));
            matcher.appendTail(replaced);
            if (replaced.length() > 4 * 1024 * 1024) {
                diagnostics.add("Maven property expansion exceeds 4 MiB: " + original);
                return original;
            }
            if (value.contentEquals(replaced)) break;
            value = replaced.toString();
        }
        if (PROPERTY.matcher(value).find()) diagnostics.add("Unresolved Maven expression: " + value);
        return value;
    }

    /** Extracts explicitly declared scalar fields without erasing expressions needed by descendant POMs. */
    private ObjectNode fields(Element element, String... names) {
        ObjectNode row = JsonNodeFactory.instance.objectNode();
        for (String name : names) if (text(element, name) != null) row.put(name, text(element, name));
        return row;
    }

    /** Uses Maven's group/artifact/type/classifier identity for dependency overrides and managed versions. */
    private String dependencyKey(JsonNode value) {
        return value.path("groupId").asText() + ":" + value.path("artifactId").asText() + ":" + value.path("type").asText("jar") + ":" + value.path("classifier").asText();
    }

    /** Reads direct element children only, avoiding accidental matches in plugin or dependency subtrees. */
    private List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent != null) for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) if (node instanceof Element element && (name == null || element.getTagName().equals(name))) result.add(element);
        return result;
    }

    /** Returns the first named direct child, or null for absent optional Maven metadata. */
    private Element child(Element parent, String name) {
        return children(parent, name).stream().findFirst().orElse(null);
    }

    /** Returns stripped direct-child text without inventing values for absent metadata. */
    private String text(Element parent, String name) {
        Element child = child(parent, name);
        return child == null ? null : child.getTextContent().strip();
    }
}
