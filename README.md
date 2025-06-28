# TastyGoClient

Pentru plati, trebuie sa configuram serverul Stripe local si sa obtinem URL-ul ngrok.

1. Pentru a expune serverul local, folosim ngrok:
    ngrok http 3000
2. Copiem URL-ul ngrok in aplicatia fisierul de configurare:
   - Adica, in fisierul java/com/examples/licenta_food_ordering/utils/network/RetrofitClient.kt modificam BASE_URL cu URL-ul ngrok.
3. Rulam server-ul Stripe:
    cd stripe-server
    node server.js
   