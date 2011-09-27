require 'fork_me_ribbon'
require 'interwiki'
require 'githuborg'
require 'githubrepo'
#require 'sassy-buttons'

Awestruct::Extensions::Pipeline.new do
    extension Awestruct::Extensions::GitHubOrg.new('arquillian', 'arquillian\-((core|showcase|maven|ajocado)|(container|extension|testrunner)\-.*)', 'module')
    extension Awestruct::Extensions::GitHubRepo.new

    extension Awestruct::Extensions::Posts.new('/blog')
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
end
