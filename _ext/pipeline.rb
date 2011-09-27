require 'fork_me_ribbon'
require 'interwiki'
#require 'sassy-buttons'

Awestruct::Extensions::Pipeline.new do
    # Posts, Paginator, Atomizer and IntenseDebate are the blog extensions
    extension Awestruct::Extensions::Posts.new('/blog')
    extension Awestruct::Extensions::Paginator.new(:posts, '/blog/index', :per_page=>5)
    extension Awestruct::Extensions::Atomizer.new(:posts, '/blog.atom')
    #extension Awestruct::Extensions::IntenseDebate.new()

    # Indexifier moves HTML files to their own directory to achieve "pretty" URLs (e.g., features.html -> /features/index.html)
    extension Awestruct::Extensions::Indexifier.new

    helper Awestruct::Extensions::Partial
    #helper Awestruct::Extensions::GoogleAnalytics
    helper Awestruct::Extensions::ForkMeRibbon
    helper Awestruct::Extensions::Interwiki
end
