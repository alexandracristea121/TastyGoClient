const express = require("express");
const cors = require("cors");
const bodyParser = require("body-parser");
const stripe = require("stripe")("sk_test_51O5AEKErOmyaHsbbBEyi0XMEiq5Rh5AGDwXM2slftIssuhooOvGlsKTjGSAAdNgUqmbidzRPDDImGTtH16OdZlmd00GRuvaEq6"); // Replace with your Stripe Secret Key

const app = express();
app.use(cors());
app.use(bodyParser.json());

// Create a payment intent
app.post("/create-payment-intent", async (req, res) => {
    try {
        const { amount, currency } = req.body;

        const paymentIntent = await stripe.paymentIntents.create({
            amount: amount, // Amount in smallest currency unit (e.g., cents)
            currency: currency,
            payment_method_types: ["card"],
        });

        res.json({ clientSecret: paymentIntent.client_secret });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Start server
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Stripe server running on port ${PORT}`);
});