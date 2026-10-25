FROM node:22-bookworm-slim AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM dependencies AS build
COPY . .
# Next.js freezes rewrites and NEXT_PUBLIC_* values at build time.
# Browsers use /backend-api; only the Next.js server contacts this Compose service.
ENV NEXT_PUBLIC_API_URL=http://backend:8080/api \
    NEXT_TELEMETRY_DISABLED=1 \
    DOCKER_BUILD=true
RUN mkdir -p public && npm run build

FROM node:22-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000 \
    NEXT_PUBLIC_API_URL=http://backend:8080/api
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
