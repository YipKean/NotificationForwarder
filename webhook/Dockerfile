FROM node:24.13.0-bookworm-slim
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev --no-fund && mkdir /data && chown node:node /data
COPY --chown=node:node server.js storage.js readRoutes.js ./
ENV HOST=0.0.0.0 PORT=3000 DATABASE_PATH=/data/notifications.sqlite NODE_ENV=production
USER node
EXPOSE 3000
CMD ["node", "server.js"]
