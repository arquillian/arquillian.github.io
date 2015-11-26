require File.join File.dirname(__FILE__), 'tweakruby'
require_relative 'awestruct_ext'
require_relative 'restclient_extensions_enabler'
require_relative 'identities'
require_relative 'jira'
require_relative 'github'
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

require 'bootstrap-sass'

Awestruct::Extensions::Pipeline.new do
  # can use engine to tune pipeline per environment by checking profile
  # the convenience methods development? and blogging? are provided
  engine = Engine.instance

  # Custom tags and syntax for textile markup
  extension Awestruct::Extensions::TextilePlus.new

  # GitHub API calls should be wrapped with credentials to up limit
  github_auth = Identities::GitHub::Auth.new('.github-auth')

  # You need to have the file $HOME/.github-auth containing a GitHub oauth token
  github_collector = Identities::GitHub::Collector.new(:teams =>
    [
      {:id => 146647, :name => 'speaker'},
      {:id => 125938, :name => 'translator'},
      {:id => 146643, :name => 'core'}
    ]
  )

  extension Awestruct::Extensions::RestClientExtensions::EnableAuth.new([github_auth])
  extension Awestruct::Extensions::RestClientExtensions::EnableGetCache.new
  extension Awestruct::Extensions::RestClientExtensions::EnableJsonConverter.new
  extension Awestruct::Extensions::Identities::Storage.new
  # the JIRA extension registers its own extensions
  Awestruct::Extensions::Jira::Project.new(self, 'ARQ:12310885')
  extension Awestruct::Extensions::Jira::ReleaseNotes.new('ARQGRA:12312222', 'graphene')
  extension Awestruct::Extensions::Jira::ReleaseNotes.new('SHRINKWRAP:12310884', 'shrinkwrap')
  extension Awestruct::Extensions::Jira::ReleaseNotes.new('SHRINKRES:12312120', 'resolver')
  extension Awestruct::Extensions::Jira::ReleaseNotes.new('SHRINKDESC:12311080', 'descriptors')
  extension Awestruct::Extensions::Github::ReleaseNotes.new('arquillian/arquillian-cube', 'cube')
  extension Awestruct::Extensions::Github::ReleaseNotes.new('arquillian/arquillian-container-chameleon', 'chameleon')
  extension Awestruct::Extensions::Github::ReleaseNotes.new('arquillian/arquillian-governor', 'governor')
  extension Awestruct::Extensions::Github::ReleaseNotes.new('arquillian/arquillian-universe-bom', 'bom')
  extension Awestruct::Extensions::Github::ReleaseNotes.new('arquillian/arquillian-extension-liferay', 'liferay')
  extension Awestruct::Extensions::Github::ReleaseNotes.new('arquillian/arquillian-container-wls', 'wls')
  extension Awestruct::Extensions::Repository::Collector.new(480465, 'd4a1e23d6ac12e6a11767d2a5521e2698e3ebd0e0262acbed3331b4ff79ffe0a', :observers => [github_collector])
  extension Awestruct::Extensions::Identities::Collect.new(github_collector)
  extension Awestruct::Extensions::Identities::Crawl.new(
    Identities::GitHub::Crawler.new,
    Identities::Gravatar::Crawler.new,
    Identities::Confluence::Crawler.new('https://docs.jboss.org/author', :auth_file => '.jboss-auth', # auth should be rewritten to use 'hidden rest auth'
        :identity_search_keys => ['name', 'username'], :assign_username_to => 'jboss_username'),
    Identities::JBossCommunity::Crawler.new
  )

  # Releases extension must be after jira and repository extensions and before posts extension 
  extension Awestruct::Extensions::Releases::Posts.new('/blog', :for_repo_owners => ['arquillian', 'shrinkwrap'], :since => '2011-01-01')
  extension Awestruct::Extensions::Releases::FutureRelease.new('/api/releases.ics')

  extension Awestruct::Extensions::Lanyrd::Search.new('arquillian')
  extension Awestruct::Extensions::Lanyrd::Export.new('/invasion/events/arquillian.ics')

  extension Awestruct::Extensions::Identities::Page.new('/community', '/community/_identity')

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
