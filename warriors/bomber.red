; name Bomber
; Strategie : copie une bombe DAT a intervalle fixe dans la memoire
; Plus simple que le Dwarf mais efficace

MOV 1, 10
; MOV 1, 10 = copie l'instruction a offset 1 (le DAT bombe)
;             vers l'instruction a offset 10 (la cible)
; Probleme : il bombarde toujours la meme case (+10)
; C'est pour ca que le Dwarf est meilleur (il fait varier la cible avec ADD)

JMP -1, 0
; JMP -1 = retourne a la case precedente → retourne au MOV
; Boucle infinie : MOV → JMP → MOV → JMP...

DAT 0, 0
; La bombe. Jamais executee directement.
; Copiee par le MOV dans la memoire ennemie.
; Quand l'ennemi tombe dessus → il meurt