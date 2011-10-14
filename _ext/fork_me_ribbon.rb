module Awestruct
  module Extensions
    module ForkMeRibbon

      # Possible to make color configurable in initializer?
      def fork_me_ribbon(site, page)
        html = ''
        if page.github_repo_owner and page.github_repo
          html += %Q(<a class="forkme" href="http://github.com/#{page.github_repo_owner}/#{page.github_repo}">)
        else
          html += %Q(<a class="forkme" href="http://github.com/#{site.github_organization}">)
        end
        html += %Q(<img style="position: absolute; top: 0; right: 0; border: 0;" src="/images/forkme_green_skewed.png" alt="Fork me on github"/>)
        html += %Q(</a>)
      end

    end
  end
end
