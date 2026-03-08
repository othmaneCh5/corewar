; name Dwarf
; Strategie : bombarde toutes les 4 cases avec des DAT
; C'est le bomber classique le plus connu en Corewar

ADD #4, 3
; ADD #4, 3 = ajoute 4 au champ B de l'instruction a offset 3
; L'instruction a offset 3 c'est le DAT (la bombe)
; Son champ B est la cible du MOV → la cible avance de 4 a chaque boucle

MOV 2, 3
; MOV 2, 3 = copie l'instruction a offset 2 (le DAT bombe)
;            vers l'instruction a offset 3 (la cible qui avance)

JMP -2, 0
; JMP -2 = saute 2 cases en arriere → retourne au ADD
; Boucle infinie : ADD → MOV → JMP → ADD → MOV → JMP...

DAT 0, 0
; C'est la bombe. Elle n'est jamais executee directement.
; Elle est copiee par le MOV dans la memoire ennemie.
; Quand l'ennemi tombe dessus → il meurt