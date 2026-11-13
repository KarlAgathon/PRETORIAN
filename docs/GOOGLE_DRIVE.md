# Google Drive za PRETORIAN (jednom, oko 5 minuta)

Google traži da svaka aplikacija koja koristi Google Drive ima svoj "OAuth klijent". Pravi se jednom, besplatno.

1. Otvori https://console.cloud.google.com i prijavi se svojim Google nalogom.
2. Gore lijevo: **Select a project › New project**, ime npr. `PRETORIAN`, **Create**.
3. Meni › **APIs & Services › Library** › potraži **Google Drive API** › **Enable**.
4. Meni › **APIs & Services › OAuth consent screen** (ili **Google Auth Platform**):
   - User type: **External**, ime aplikacije `PRETORIAN`, tvoj mail kao support i developer mail, **Save**.
   - **Audience / Publishing status**: klikni **Publish app** (In production). Inače prijava traje samo 7 dana.
5. Meni › **APIs & Services › Credentials › Create credentials › OAuth client ID**:
   - Application type: **TVs and Limited Input devices**, ime `PRETORIAN TV`, **Create**.
   - Kopiraj **Client ID** i **Client secret**.
6. Ubaci ih u PRETORIAN, na jedan od dva načina:
   - **Najlakše (za sve buduće verzije):** na laptopu u terminalu:
     ```
     gh secret set GOOGLE_CLIENT_ID -R KarlAgathon/PRETORIAN
     gh secret set GOOGLE_CLIENT_SECRET -R KarlAgathon/PRETORIAN
     ```
     (svaka komanda pita da zalijepiš vrijednost). Sljedeći update ih ima ugrađene.
   - **Ili u aplikaciji:** Postavke › Profili i Google Drive › Google OAuth klijent (tipkaj s telefona: Google TV aplikacija › Daljinski › tastatura).
7. U PRETORIAN-u: klik na sliku profila gore lijevo › **Prijavi se Google nalogom**. Na telefonu otvori `google.com/device`, upiši kod s TV-a, izaberi nalog.
   Google može napisati "Google hasn't verified this app": klikni **Advanced › Go to PRETORIAN (unsafe)**. To je tvoja vlastita aplikacija, ništa ne ide drugima.

Šta se čuva: sve postavke, playliste, svi profili (favoriti, gledano, izgled), u skrivenom folderu aplikacije na tvom Google Driveu. Niko osim PRETORIAN-a na tvom nalogu to ne vidi.
