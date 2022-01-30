# FaucETH

Faucet for EVM chains

# Why

I had a lot of problems with existing simple faucets (the ones that do not force you to use some centralized service like twitter) and had the urge to create one that does not suffer from these problems for a while. 
Then [a tweet of pari](https://twitter.com/parithosh_j/status/1471888173366235143) in context of faucet problems for the kintsugi merge testnet gave the spark to finally code the faucet.

# What

This faucet uses hCaptcha instead of reCaptcha to not feed the google.
You can deeplink to a cain with the url parameter `chain` that contains a chain-id.
And you can pass an address with the url parameter `address` that will then be pre-filled.

# Run

## via docker

`docker run -p 8080:8080 ghcr.io/komputing/fauceth:release`

## via distribution

Either download the tar from the release then after untar run:

`./bin/fauceth`

you can also build the tar via:

`./gradlew distTar`

## from source

or directly run tha app from within the repo via:

`./gradlew run`

# Configure

You can set properties of FaucETH via a file called `fauceth.properties`, via environment variables or system properties.

You should set `app.chains` with a comma separated list of chain-ids and also `hcaptcha.secret` and `hcaptcha.sitekey` so you are protected by hcaptcha from faucet drainage.

Optional properties you can set are `app.title`, `app.imageURL`, `app.amount`, `app.logging`, `app.port`, `app.keywords`, `app.footer` or `app.ethkey`
In `app.title` you can use `%CHAINNAME`, `%CHAINTITLE`, `%CHAINSHORTNAME` - this is especially useful if you support multiple chains.
If your chains use infura RPCs please also set `infura.projectid`

After you started the app you can see the address by accessing `locahost:8080/address` - this way you can initially fund it.
There is a status dashboard on `locahost:8080/status`.

# Dream

What would be real nice would be a web3 faucet - if anyone has ideas how to realize it please contact me. The problem I cannot overcome is how to validate a captcha without a backend.

Yea you could use a PoH blockchain like idena or a dapp like Proof of Humanity or brightId - but often you need the faucet to get started with crypto - so there is a hen and egg problem here.

# License

AGPLv3
