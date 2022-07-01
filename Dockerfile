FROM fedora:35

LABEL org.opencontainers.image.title="Arquillian.org website generator"
LABEL org.opencontainers.image.description="This is image provides a build tool chain for arquillian.org website. Follow the README from the repository for details"
LABEL org.opencontainers.image.licenses="Apache-2.0"
LABEL org.opencontainers.image.authors="Bartosz Majsak <bartosz@redhat.com>"


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

ENV HOME /home/dev

WORKDIR $HOME

# Install RVM
RUN curl -sSL https://rvm.io/mpapis.asc | gpg2 --import -
RUN curl -sSL https://rvm.io/pkuczynski.asc | gpg2 --import -
RUN curl -L get.rvm.io | bash -s stable --autolibs=read-only

ENV RUBY_VERSION 2.5.9
RUN bash -l -c "rvm install $RUBY_VERSION"
RUN bash -l -c "rvm use $RUBY_VERSION"
RUN bash -l -c "rvm cleanup all"

# Install Rake and Bundler for driving the Awestruct build & site
RUN bash -l -c "gem install -N bundler rake"

ENV AWESTRUCT_VERSION 0.6.1
RUN bash -l -c "gem install awestruct -v $AWESTRUCT_VERSION"

RUN echo 'alias install-gems="bundle install -j 10 --path ./.gems"' >> $HOME/.bashrc
RUN source $HOME/.bashrc

EXPOSE 4242

CMD ["bash", "--login"]
