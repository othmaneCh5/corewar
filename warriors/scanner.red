; name Scanner
; Strategie : cherche l'ennemi dans la memoire et tire dessus
; Plus intelligent qu'un bomber car il cible precisement l'ennemi

ADD #10, 4
; Avance le pointeur de scan de 10 cases a chaque boucle

CMP 3, 0
; Compare l'instruction a offset 3 avec l'instruction a offset 0
; offset 0 = DAT 0,0 (case vide)
; Si elles sont egales → la case scannee est vide → ennemi pas la
; Si elles sont differentes → ennemi detecte → on saute au tir

JMP -2, 0
; Retourne au ADD pour continuer le scan (ennemi pas trouve)

MOV 4, 3
; Tire une bombe DAT sur la cible detectee

JMP -4, 0
; Retourne au debut

DAT 0, 0
; La bombe