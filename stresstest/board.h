#ifndef _great_gatsby_board
#define _great_gatsby_board

typedef struct board {

    char name[400];
    char board[4000];
    int size ; 

} Board;

void start();
Board get(int i);
int len();
void shuffle(int m, int pieces);

#endif 