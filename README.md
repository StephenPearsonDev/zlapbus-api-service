# ZTM API

This service reads live vehicle data from Redis, caches it in memory, and exposes it over HTTP using Spring WebFlux. Clients can fetch data via REST or stream updates using Server-Sent Events (SSE) using Protobuf or JSON.


## Live site

[https://zlapbus.netlify.app](https://zlapbus.netlify.app)


## What it does

- Reads vehicle data from Redis
- Groups vehicles by line and type
- Serves data over REST and SSE
- Supports Protobuf and JSON
- Lets clients filter by line


## License

MIT
