module Awestruct
  module Extensions
    module ForkMeRibbon

      # Possible to make color configurable in initializer?
      def fork_me_ribbon(site, page)
        html = ''
        if page.github_repo_owner and page.github_repo
          html += %Q(<a id="forkme" href="http://github.com/#{page.github_repo_owner}/#{page.github_repo}">)
        else
          html += %Q(<a id="forkme" href="http://github.com/#{site.github_organization}">)
        end
        html += %Q(<img src="/images/forkme_white.png" alt="Fork me on github"/>)
        html += %Q(</a>)
      end

    end
  end
end
