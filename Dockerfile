FROM node:24.19.0-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --legacy-peer-deps
COPY tsconfig.json nest-cli.json ./
COPY src ./src
RUN npm run build
USER node
EXPOSE 8088
CMD ["node", "dist/main.js"]