require 'fork_me_ribbon'
#require 'sassy-buttons'

Awestruct::Extensions::Pipeline.new do
    extension Awestruct::Extensions::Posts.new('/blog')
    extension Awestruct::Extensions::Paginator.new(:posts, '/blog/index', :per_page=>5)
    extension Awestruct::Extensions::Atomizer.new(:posts, '/blog.atom')
    extension Awestruct::Extensions::Indexifier.new
    #extension Awestruct::Extensions::IntenseDebate.new()

    helper Awestruct::Extensions::Partial
    #helper Awestruct::Extensions::GoogleAnalytics
    helper Awestruct::Extensions::ForkMeRibbon
end
