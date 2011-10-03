require 'common'
require 'page_debug'
require 'fork_me_ribbon'
require 'interwiki'
require 'github'
require 'jira'
require 'arquillian'
require 'posts2'
#require 'sassy-buttons'

Awestruct::Extensions::Pipeline.new do
    extension Awestruct::Extensions::GitHub::Org.new(
        'arquillian',
        'arquillian\-((core|showcase|maven|ajocado)|(container|extension|testrunner)\-.*)',
        'module',
        'html.haml',
        # Reg Exp mapping between repo_name and type of module layout
        [
          [/.*\-container\-.*/, 'container-module'],
          [/.*\-testrunner\-.*/, 'testrunner-module'],
          [/.*\-extension\-.*/, 'extension-module']
        ]
    )
    extension Awestruct::Extensions::GitHub::Contributor.new
    extension Awestruct::Extensions::GitHub::Repo.new('([0-9]+\.[0-9]+).*')
    extension Awestruct::Extensions::Jira::ReleaseNotes.new('ARQ', '12310885')
    extension Awestruct::Extensions::GitHub::Release.new('blog', 'textile', '2011-08-14')
    extension Awestruct::Extensions::Arquillian::JiraVersionPrefix.new

    extension Awestruct::Extensions::Arquillian::TagInfo.new

    # Posts2 is a 'patched' version of Posts to support generating the date layout from page.date as well as filename
    # GitHub:Release will generate synth pages with name 'repo'-'tag'.textile, the date is based on the tag commit date
    extension Awestruct::Extensions::Posts2.new('/blog')
    extension Awestruct::Extensions::Paginator.new(:posts, '/blog/index', :per_page=>5)
    extension Awestruct::Extensions::Atomizer.new(:posts, '/blog.atom')
    extension Awestruct::Extensions::Tagger.new(:posts, '/blog/index', '/blog/tags', :per_page=>5)
    extension Awestruct::Extensions::TagCloud.new(:posts, '/blog/tags/index.html')
    #extension Awestruct::Extensions::IntenseDebate.new()

    # Indexifier moves HTML files to their own directory to achieve "pretty" URLs (e.g., features.html -> /features/index.html)
    extension Awestruct::Extensions::Indexifier.new

    helper Awestruct::Extensions::Partial
    #helper Awestruct::Extensions::GoogleAnalytics
    helper Awestruct::Extensions::ForkMeRibbon
    helper Awestruct::Extensions::Interwiki
    helper Awestruct::Extensions::PageDebug
end
