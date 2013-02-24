require File.join File.dirname(__FILE__), 'tweakruby'
require_relative 'restclient_extensions_enabler'
require_relative 'identities'
require_relative 'jira'
require_relative 'repository'
require_relative 'arquillian'
require_relative 'releases'
require_relative 'patched_atomizer'
require_relative 'autotag'
require_relative 'common'
require_relative 'guide'
require_relative 'lanyrd'
require_relative 'interwiki'
require_relative 'textile_plus'
require_relative 'disqus_more'
require_relative 'posts_helper'
require_relative 'edit_page'
require_relative 'asset_fingerprinter'
#require_relative 'cache_evolver'
#require_relative 'page_debug'
#require_relative 'fork_me_ribbon'

Awestruct::Extensions::Pipeline.new do
  # Custom tags and syntax for textile markup
  extension Awestruct::Extensions::TextilePlus.new

  # GitHub API calls should be wrapped with credentials to up limit
  github_auth = Identities::GitHub::Auth.new('.github-auth')

  # You need to have the file $HOME/.github-auth containing username:password on one line
  github_collector = Identities::GitHub::Collector.new(:auth => github_auth, :teams =>
    [
      {:id => 146647, :name => 'speaker'},
      {:id => 125938, :name => 'translator'},
      {:id => 146643, :name => 'core'}
    ]
  )

  extension Awestruct::Extensions::RestClientExtensions::EnableGetCache.new
  extension Awestruct::Extensions::RestClientExtensions::EnableJsonConverter.new
  extension Awestruct::Extensions::Identities::Storage.new
  # the JIRA extension registers its own extensions
  Awestruct::Extensions::Jira::Project.new(self, 'ARQ:12310885')
  extension Awestruct::Extensions::Jira::ReleaseNotes.new('ARQGRA:12312222', 'graphene')
  extension Awestruct::Extensions::Repository::Collector.new(480465, 'sGiJRdK2Cq8Nz0TkTNAKyw', :observers => [github_collector], :auth => github_auth)
  extension Awestruct::Extensions::Identities::Collect.new(github_collector)
  extension Awestruct::Extensions::Identities::Crawl.new(
    Identities::GitHub::Crawler.new(:auth => github_auth),
    Identities::Gravatar::Crawler.new,
    Identities::Confluence::Crawler.new('https://docs.jboss.org/author', :auth_file => '.jboss-auth',
        :identity_search_keys => ['name', 'username'], :assign_username_to => 'jboss_username'),
    Identities::JBossCommunity::Crawler.new
  )

  # Releases extension must be after jira and repository extensions and before posts extension 
  extension Awestruct::Extensions::Releases::Posts.new('/blog', :for_repo_owners => ['arquillian'], :since => '2011-01-01')

  extension Awestruct::Extensions::Lanyrd::Search.new('arquillian')
  extension Awestruct::Extensions::Lanyrd::Export.new('/invasion/events/arquillian.ics')

  extension Awestruct::Extensions::Posts.new('/blog')
  extension Awestruct::Extensions::AutoTag.new(:posts)
  extension Awestruct::Extensions::Paginator.new(:posts, '/blog/index', :per_page => 5)
  extension Awestruct::Extensions::Tagger.new(:posts, '/blog/index', '/blog/tags', :per_page => 5)
  extension Awestruct::Extensions::TagCloud.new(:posts, '/blog/tags/index.html')
  extension Awestruct::Extensions::Disqus.new

  # Indexifier moves HTML files to their own directory to achieve "pretty" URLs (e.g., features.html -> /features/index.html)
  extension Awestruct::Extensions::Indexifier.new

  # Needs to be after Indexifier to get the links correct
  # FIXME we need a patched atomizer to carry over our custom fields (release & component)
  extension Awestruct::Extensions::PatchedAtomizer.new(:posts, '/blog/atom.xml', :additional_tags => ['arquillian'])

  # Needs to be after Indexifier to get the linking correct; second argument caps changelog per guide
  extension Awestruct::Extensions::Guide::Index.new('/guides', 15)

  # Must be after all other extensions that might populate identities
  extension Awestruct::Extensions::Identities::Cache.new

  # Transformers
  transformer Awestruct::Extensions::Minify.new([:js])

  # Helpers
  helper Awestruct::Extensions::PostsHelper
  helper Awestruct::Extensions::Partial
  helper Awestruct::Extensions::Interwiki
  helper Awestruct::Extensions::GoogleAnalytics
  helper Awestruct::Extensions::EditPage
  #helper Awestruct::Extensions::CacheEvolver
  helper Awestruct::Extensions::AssetFingerprinter
end
