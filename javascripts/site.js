/**
 * Pushes the footer to the bottom of the page if the page is shorter than the
 * height of the browser window.
 */
function footergravity() {
  $('#main').css('min-height', $(window).height() - $('footer').outerHeight() - parseInt($('#main').css('margin-top')));
}

function activateFooterGravity() {
  footergravity();
  $(window).resize(footergravity);
}

function activateTooltips() {
  $('*[rel=tooltip]').tooltip();
}

/**
 * Redirect to the base path of the index.html page to normalize web stats.
 */
function redirectToIndexPath() {
  if (window.location.pathname.search(/\/index.html$/) != -1) {
    window.location.href = window.location.href.replace('index.html', '')
  }
}

function activateSlideshow() {
  var start = window.location.hash ? window.location.hash.replace('#', '') : 1;
  if (start > 1) {
    var items = $('#slideshow .item');
    $(items[start - 1]).addClass('active');
    $(items[0]).removeClass('active');
  }
  $('#slideshow').carousel({
    pause: ''
  })
  .on('slid', function(e) {
    var $active = $('#slideshow').find('.active');
    var pos = $active.parent().children().index(active);
    window.location.hash = '#' + (pos + 1);
  })
  .carousel('pause');
}

function toggleGuideMenu() {
  $menu = $('#guides');
  if ($menu.css('display') == 'none') {
    $menu.css('display', 'block');
    $('body').click(toggleGuideMenu);
  } else {
    $menu.css('display', 'none');
    $('body').unbind('click');
  }
  return false;
}

function activateGuideMenuControl() {
  $('#guides_menu, .guides_menu').click(toggleGuideMenu);
}

function activateScrollingToc(sections) {
  var $toc = $('#toc');
  $toc.localScroll({hash: true});
  var currentSection = false;
  var sectionOffsets = {};
  $.each(sections, function(idx, section) {
    sectionOffsets[section] = $('#' + section).position().top;
  });
  
  var tocMetrics = {
    top: $toc.position().top,
    height: $toc.outerHeight()
  };
  var bottomBumper = $('#guide').outerHeight() + $('#guide').offset().top - 25;
  var $firstLink = $('#toc a').first();
  var linkColor = $firstLink.css('color');
  var linkTextDecoration = $firstLink.css('text-decoration');
  var selectedColor = $firstLink.parent().css('color');
  var selectedTextDecoration = (linkTextDecoration == 'underline' ? 'none' : 'underline');

  var updateToc = function() {
    var scrollY = $(window).scrollTop();
    positionToc(scrollY);
    highlightSectionInToc(scrollY);
  }

  var positionToc = function(scrollY) {
    // if scrolled past toc, move it down
    if (scrollY > tocMetrics.top) {
      if ($toc.css('position') != 'fixed') {
        $toc.css('position', 'fixed');
      }
      var remainingHeight = bottomBumper - scrollY;
      // keep toc from overrunning bottom of content
      if (remainingHeight < tocMetrics.height) {
        $toc.css('top', 0 - (tocMetrics.height - remainingHeight));
      }
      else {
        $toc.css('top', 5);
      }
    }
    else {
      if ($toc.css('position') != 'static') {
        $toc.css({position: 'static', top: 0});
      }
    }
  };

  var highlightSectionInToc = function(scrollY) {
    var numSections = sections.length;
    if (numSections == 0) {
      return;
    }

    // if scrolled above first section, unhighlight any
    if (scrollY < sectionOffsets[sections[0]]) {
      toggleSelection(false);
    }
    // if last section is in view, highlight it
    // tweak to get last section to be highlighted in toc when scrolled to bottom of page
    //else if (scrollY + $(window).height() > sectionOffsets[sections[numSections - 1]] &&
    //    scrollY - 100 > sectionOffsets[sections[numSections - 2]]) {
    //  toggleSelection(sections[numSections - 1]);
    //}
    else if (scrollY + $(window).height() == $(document).height()) {
      toggleSelection(sections[numSections - 1]);
    }
    // highlight visible section
    else {
      $.each(sections, function(idx, section) {
        if (scrollY > sectionOffsets[section] && (idx == numSections - 1 || scrollY < sectionOffsets[sections[idx + 1]])) {
          toggleSelection(section);
        }
      });
    }
  }

  var toggleSelection = function(section) {
    if (!section || section != currentSection) {
      if (currentSection) {
        $('#' + currentSection + '_link').css({textDecoration: linkTextDecoration, color: linkColor});
      }
      currentSection = section;
      if (section) {
        $('#' + section + '_link').css({textDecoration: selectedTextDecoration, color: selectedColor});
      }
    }
  }

  updateToc();
  $(window).scroll(updateToc);
}

function activateToTopControl() {
  var toTopLocked = true;
  var bannerFixed = $('#banner').css('position') == 'fixed';
  var showOffset = (bannerFixed ? $('#banner').height() + 20 : 20) + 'px'
  var hideOffset = '-50px';
  var triggerOffset = 250;
  $(window).bind('scroll', function() {
     $toTop = $('#toTop');
     if ($(this).scrollTop() >= triggerOffset)   {
        if (toTopLocked) {
           $toTop.animate({top: showOffset});
        }
        toTopLocked = false;
     }
     else {
        if (!toTopLocked) {
           $toTop.animate({top: hideOffset}); 
        }
        toTopLocked = true;
     }
  });

  $('#toTop').click(function() {
    $('html, body').animate({ scrollTop: 0 }, 'slow');
    return false;
  });
}
