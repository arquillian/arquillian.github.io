/** Replaces the selected form with its Maven POM while retaining the legacy submit hook. */
function generate(form) {
  write_code_block(create_pom(form), form);
  return false;
}

/** Escapes raw snapshot and DOM values so generated Maven XML remains text rather than executable markup. */
function escapeXml(value) {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll("'", '&apos;').replaceAll('"', '&quot;');
}

/** Builds the published Maven project with only checked Universe POM dependencies in visible form order. */
function create_pom(form) {
  var pom = '<?xml version="1.0" encoding="UTF-8"?>\n';
  pom += '<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n';
  pom += '    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">\n';
  pom += '\n';
  pom += '  <modelVersion>4.0.0</modelVersion>\n';
  pom += '  <groupId>org.test</groupId>\n';
  pom += '  <artifactId>arquillian-universe-test</artifactId>\n';
  pom += '  <name>Arquillian Example</name>\n';
  pom += '  <version>1.0.0.Final-SNAPSHOT</version>\n';
  pom += '  <packaging>jar</packaging>\n';
  pom += '\n';
  pom += '  <properties>\n';
  pom += '    <version.arquillian_universe>' + escapeXml(form.getAttribute('data-universe-version')) + '</version.arquillian_universe>\n';
  pom += '  </properties>\n';
  pom += '\n';
  pom += '  <dependencyManagement>\n';
  pom += '    <dependencies>\n';
  pom += '      <dependency>\n';
  pom += '        <groupId>org.arquillian</groupId>\n';
  pom += '        <artifactId>arquillian-universe</artifactId>\n';
  pom += '        <version>${version.arquillian_universe}</version>\n';
  pom += '        <scope>import</scope>\n';
  pom += '        <type>pom</type>\n';
  pom += '      </dependency>\n';
  pom += '    </dependencies>\n';
  pom += '  </dependencyManagement>\n';
  pom += '\n';
  pom += '  <dependencies>\n';
  for (var i = 0; i < form.length; i++) {
    var input = form[i];
    if (input.type !== 'checkbox' || !input.checked) continue;
    pom += '    <dependency>\n';
    pom += '      <groupId>' + escapeXml(input.getAttribute('data-group-id')) + '</groupId>\n';
    pom += '      <artifactId>' + escapeXml(input.id) + '</artifactId>\n';
    pom += '      <scope>test</scope>\n';
    pom += '      <type>pom</type>\n';
    pom += '    </dependency>\n';
  }
  pom += '  </dependencies>\n';
  pom += '</project>\n';
  return pom;
}

/** Inserts POM text through DOM textContent before the existing local syntax highlighter decorates it. */
function write_code_block(pom, form) {
  var pre = document.createElement('pre');
  pre.className = 'prettify';
  var code = document.createElement('code');
  code.className = 'prettify';
  code.textContent = pom;
  pre.appendChild(code);
  form.replaceChildren(pre);
  prettify();
}
