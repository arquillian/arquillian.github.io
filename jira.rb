require 'jira'

username = "aslak"
password = "tuXhuset1k"

options = {
            :username => username,
            :password => password,
            :site     => 'https://issues.jboss.org/',
            :context_path => '',
            :auth_type => :basic
          }

client = JIRA::Client.new(options)


project_id = 'ARQ'
version_name = 'tomcat_1.0.0.Final'


project = client.Project.find(project_id)
version = project.versions.find{ |x| x.name.eql? version_name }

version_issues = client.Issue.jql("project = #{project_id} AND fixVersion = '#{version_name}'")

puts "Issues on version #{version_name}: #{version_issues.size}"

puts version_issues.first

version_issues.each do |issue|
  puts "#{issue.key} #{issue.resolution unless issue.resolution.nil?}"
end