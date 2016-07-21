FROM fedora:24

RUN dnf -y update && dnf clean all
RUN dnf -y install \
  autoconf \
  automake \
  bison \
  bzip2 \
  gcc-c++ \
  git \
  glibc-devel \
  glibc-headers \
  libffi-devel \
  libtool \
  libxml2-devel \
  libxslt-devel \
  libyaml-devel \
  make \
  nmap \	
  openssl-devel \
  patch \
  patch \
  procps \
  readline-devel \
  sqlite-devel \
  which \
  v8-devel \
  zlib-devel \
&& dnf clean all


RUN groupadd -r dev && useradd  -g dev -u 1000 dev
RUN mkdir -p /home/dev
RUN chown dev:dev /home/dev

USER dev

ENV HOME /home/dev
ENV RUBY_VERSION 2.3.1

### Not released yet - but keeping it here for further simplification
ENV AWESTRUCT_VERSION 0.6.0 

# Used for custom build
ENV AWESTRUCT_REPO https://github.com/awestruct/awestruct.git
ENV AWESTRUCT_REPO_DIR $HOME/awestruct
ENV AWESTRUCT_COMMIT a014121

# Fix encoding
ENV LANG en_US.UTF-8
ENV LC_ALL en_US.UTF-8
ENV LANGUAGE en_US.UTF-8

WORKDIR $HOME

RUN gpg2 --keyserver hkp://keys.gnupg.net --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3
RUN curl -sSL https://get.rvm.io | /bin/bash -s stable --ruby=$RUBY_VERSION 
RUN bash -l -c "rvm use $RUBY_VERSION"
RUN bash -l -c "rvm cleanup all"
# Install Rake and Bundler for driving the Awestruct build & site
RUN bash -l -c "gem install -N bundler rake"

# Run custom Awestuct build until v0.6.0 is released
RUN bash -l -c "git clone $AWESTRUCT_REPO $AWESTRUCT_REPO_DIR"
RUN bash -l -c "cd $AWESTRUCT_REPO_DIR && git checkout $AWESTRUCT_COMMIT && gem build awestruct.gemspec"
RUN bash -l -c "gem install $AWESTRUCT_REPO_DIR/awestruct-0.6.0.alpha.gem --no-rdoc --no-ri"

# Once released
# RUN bash -l -c "gem install awestruct -v $AWESTRUCT_VERSION --no-rdoc --no-ri"

EXPOSE 4242

CMD ["bash", "--login"]
