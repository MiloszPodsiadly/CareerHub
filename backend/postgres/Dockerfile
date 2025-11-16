FROM postgres:16-alpine
RUN apk add --no-cache shadow && \
    (passwd -l root || true) && \
    (usermod -s /sbin/nologin root || true)
RUN mkdir -p /var/run/postgresql /tmp && \
    chown -R postgres:postgres /var/run/postgresql /tmp && \
    chmod 775 /var/run/postgresql && \
    chmod 1777 /tmp
USER postgres
WORKDIR /var/lib/postgresql/data
EXPOSE 5432

