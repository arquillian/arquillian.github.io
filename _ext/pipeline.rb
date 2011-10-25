require 'common'
require 'page_debug'
require 'fork_me_ribbon'
require 'interwiki'
require 'github'
require 'jira'
require 'arquillian'
require 'arquillian_model'
require 'guide'
require 'lanyrd'
require 'sassy-buttons'

Awestruct::Extensions::Pipeline.new do
    # The GitHub::Org extension is development page refreshes to break (requires double save)
    extension Awestruct::Extensions::GitHub::Org.new(
        'arquillian',
        #'arquillian\-((core|showcase|maven|ajocado)|(container|extension|testrunner)\-.*)',
        'arquillian\-((core|showcase|maven)|(container|extension|testrunner)\-(?!reloaded).+)',
        #'arquillian\-((core)|(extension-drone))',
        'module',
        'html.haml',
        # Reg Exp mapping between repo_name and type of module layout
        [
          [/.*\-core/, 'core-module'],
          [/.*\-container\-.*/, 'container-module'],
          [/.*\-testrunner\-.*/, 'testrunner-module'],
          [/.*\-extension\-.*/, 'extension-module']
        ]
    )
    extension Awestruct::Extensions::GitHub::Contributor.new
    extension Awestruct::Extensions::GitHub::Repo.new('([0-9]+\.[0-9]+).*')
    extension Awestruct::Extensions::Jira::ReleaseNotes.new('ARQ', '12310885')
    extension Awestruct::Extensions::GitHub::Release.new('blog', 'textile', '2010-09-14')
    extension Awestruct::Extensions::Arquillian::JiraVersionPrefix.new

    extension Awestruct::Extensions::Lanyrd::Search.new('arquillian')

    extension Awestruct::Extensions::Arquillian::TagInfo.new
    extension Arquillian::Model::Bind.new

    extension Awestruct::Extensions::Posts.new('/blog')
    extension Awestruct::Extensions::Paginator.new(:posts, '/blog/index', :per_page=>5)
    extension Awestruct::Extensions::Atomizer.new(:posts, '/blog.atom')
    extension Awestruct::Extensions::Tagger.new(:posts, '/blog/index', '/blog/tags', :per_page=>5)
    extension Awestruct::Extensions::TagCloud.new(:posts, '/blog/tags/index.html')
    #extension Awestruct::Extensions::IntenseDebate.new()

    # Indexifier moves HTML files to their own directory to achieve "pretty" URLs (e.g., features.html -> /features/index.html)
    extension Awestruct::Extensions::Indexifier.new

    # Needs to be after Indexifier to get the linking correct
    extension Awestruct::Extensions::Guide::Index.new('/guides')

    #helper Awestruct::Extensions::Partial
    helper Awestruct::Extensions::GoogleAnalytics
    helper Awestruct::Extensions::ForkMeRibbon
    helper Awestruct::Extensions::Interwiki
    helper Awestruct::Extensions::PageDebug

    transformer Awestruct::Extensions::Guide::AddIds.new
end
