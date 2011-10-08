module Awestruct
  module Extensions
    module ForkMeRibbon

      # Possible to make color configurable in initializer?
      def fork_me_ribbon()
        html = ''
        html += %Q(<a class="forkme" href="#{site.source_repo}">)
        html += %Q(<img style="position: absolute; top: 0; right: 0; border: 0;" src="/images/forkme_green_skewed.png" alt="Fork me on github"/>)
        html += %Q(</a>)
      end

    end
  end
end
