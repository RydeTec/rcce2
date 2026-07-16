# RCCE2 headless Rust server — Linux container image.
#
# The server is a thin, GUI-less tick loop (logs to stdout), so it runs headless
# in a container with no display. Build context is the REPO ROOT (the server-rs
# workspace path-depends on the sibling client-rs crates):
#
#   docker build -f server-rs/Dockerfile -t rcce-server .
#   docker run --rm -p 25000:25000/udp -v "$(pwd)/data:/data" rcce-server
#
# The project data tree (worlds, actors, accounts) is mounted at /data; the
# server reads it via RCCE_DATA. Without a mount it still boots (empty accounts,
# no catalog) but characters can't be created until data is provided.

# ---- build stage -----------------------------------------------------------
FROM rust:1.85.0-bookworm AS build
WORKDIR /src
# The shared wire/format crates (path deps) + the server workspace. The client
# workspace manifest is needed so Cargo can resolve `*.workspace = true`
# inheritance for the three reused crates; the other client members are parsed
# but never built (they aren't in the server's dependency graph).
COPY client-rs ./client-rs
COPY server-rs ./server-rs
WORKDIR /src/server-rs
# enet-sys compiles the vendored ENet fork via the `cc` crate — gcc ships in the
# rust:1.85.0-bookworm image, so no extra apt packages are required.
RUN cargo build --release --locked --bin rcce-server \
    && strip target/release/rcce-server

# ---- runtime stage ---------------------------------------------------------
FROM debian:bookworm-slim AS runtime
# Non-root runtime user.
RUN useradd --create-home --uid 10001 rcce
COPY --from=build /src/server-rs/target/release/rcce-server /usr/local/bin/rcce-server
# Project data is mounted here at run time.
ENV RCCE_DATA=/data
# Authoritative UDP game port (matches Misc.dat / the default 25000).
EXPOSE 25000/udp
USER rcce
ENTRYPOINT ["/usr/local/bin/rcce-server"]
