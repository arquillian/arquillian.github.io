FROM fedora:24
MAINTAINER Bartosz Majsak <bartosz@redhat.com>

LABEL Description="This is image provides a build tool chain for arquillian.org website. Follow the README from the repository for details"

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
  ImageMagick-devel \
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

# Environment variables

ENV HOME /home/dev
ENV RUBY_VERSION 2.4.3

ENV AWESTRUCT_VERSION 0.6.1

# Fix encoding
ENV LANG en_US.UTF-8
ENV LC_ALL en_US.UTF-8
ENV LANGUAGE en_US.UTF-8

WORKDIR $HOME

RUN gpg2 --keyserver hkp://keys.gnupg.net --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3
RUN gpg2 --keyserver hkp://pool.sks-keyservers.net --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 7D2BAF1CF37B13E2069D6956105BD0E739499BDB
RUN curl -sSL https://get.rvm.io | bash -s stable --ruby=$RUBY_VERSION
RUN bash -l -c "rvm use $RUBY_VERSION"
RUN bash -l -c "rvm cleanup all"
# Install Rake and Bundler for driving the Awestruct build & site
RUN bash -l -c "gem install -N bundler rake"

RUN bundle config set path './.gems'
RUN echo 'alias install-gems="bundle install -j 10"' >> $HOME/.bashrc
RUN source $HOME/.bashrc

# Install Awestruct
RUN bash -l -c "gem install awestruct -v $AWESTRUCT_VERSION"

EXPOSE 4242

CMD ["bash", "--login"]
